# un-reminder-worker

Cloudflare Worker proxy for Un-Reminder. Generates varied notification text via Requesty.ai (OpenAI-compatible).

## Development

```bash
npm install
npm run dev
```

## Type-check

```bash
npm run typecheck
```

## Tests

```bash
npm test
```

## Deploy

```bash
npm run deploy
```

## Setup

### 1. Create KV namespaces

```bash
wrangler kv namespace create UR_SPEND    # spend counters
wrangler kv namespace create UR_TOKENS   # per-user auth tokens
```

Copy each returned `id` into `wrangler.toml` under the matching `[[kv_namespaces]]` block.

### 2. Set secrets

```bash
wrangler secret put UR_REQUESTY_KEY    # Requesty.ai API key
```

### 3. Tokens

Every user of the app holds their own token, which they paste into the app's Cloud AI settings.
The Worker stores only a salted SHA-256 hash of it, so the token is printed exactly once when
minted; keep it or mint another.

```bash
npm run tokens -- mint --label <name>   # prints ur1_<id>_<secret> once and stores its hash
npm run tokens -- disable <id>          # revoke: the token answers 401 from the next request
npm run tokens -- enable <id>
npx wrangler kv key list --binding UR_TOKENS --remote   # ids of every minted token
```

`<id>` is the 16-hex-character middle part of the token; the app shows the stored token's
`ur1_<id>` prefix so a user can tell you which one to revoke without revealing the secret.

### 4. Environment variables

Configured in `wrangler.toml` under `[vars]`:

| Variable | Default | Description |
|----------|---------|-------------|
| `UR_MODEL` | `google/gemini-3.6-flash` | Model to use via Requesty |
| `UR_DAILY_CAP_CENTS` | `50` | Max daily spend in cents |
| `UR_MONTHLY_CAP_CENTS` | `500` | Max monthly spend in cents |
| `UR_GENERATION_VERSION` | `2` | Integer ≥ 1 identifying the current model + prompt; echoed on `/v1/health` and `/v1/generate/batch` |

**Rolling out a new model or prompt:** bump `UR_GENERATION_VERSION` in the same deploy that changes
`UR_MODEL` or `buildPrompt`. Every device checks the version daily and regenerates each active habit's
pool that was generated under another version, paced about one habit per minute, keeping the old pool
live until the new batch lands. A value that is missing, non-integer or below 1 makes both routes
answer 503 (the app reserves `0` for rows generated before versions existed).

**Generation version history** — add a row with every bump so a copy regression can be traced to the
model or prompt that produced it. Pricing constants in `src/lib/requesty.ts` follow `UR_MODEL`.

| Version | Model | Prompt | Date | Change |
|---------|-------|--------|------|--------|
| `1` | `google/gemini-3-flash-preview` | `buildPrompt` as of #391 (shapes #373, mode tags #374) | 2026-04 | Initial model, never revisited since the first Worker deploy |
| `2` | `google/gemini-3.6-flash`, `reasoning_effort: low` | Unchanged from `1` | 2026-09 | #376 model upgrade; thinking bounded and reserved inside `max_tokens` |

### 5. Rate limiting (optional)

Configure rate limiting rules at the Cloudflare zone dashboard level (not in Worker code).

## API

### `GET /v1/health`

Returns worker status, current daily spend and the deployed `generationVersion`.

### `POST /v1/generate/batch`

Generates notification text variants. Requires `Authorization: Bearer <token>`.

**Request body:**

```json
{
  "habitTitle": "Morning stretch",
  "habitTags": ["fitness", "morning"],
  "locationName": "Home",
  "timeOfDay": "morning",
  "supportedModes": ["WALKING", "SITTING"],
  "n": 3
}
```

`supportedModes` lists the activity modes (`WALKING`, `SITTING`, `TRANSPORT`) the habit is done in; omit it, or send an empty array, for a habit done in any of them.

**Response:**

```json
{
  "variants": [
    { "text": "Got 5 minutes for a full-body stretch?", "shape": "QUESTION", "modes": [] },
    { "text": "Hold a downward dog for 60 seconds", "shape": "TIMEBOXED", "modes": ["SITTING"], "actionUrl": "https://www.youtube.com/results?search_query=downward+dog+yoga+form" },
    { "text": "Roll your shoulders 10 times as you walk", "shape": "STATEMENT", "modes": ["WALKING"] }
  ],
  "generationVersion": 2
}
```

`generationVersion` is the version these variants were generated under; the app stamps each stored
row with it and regenerates a habit's pool once the deployed version moves on.

Each variant has a `text` field, a `shape` field, a `modes` field and an optional `actionUrl` field. `shape` is one of `QUESTION`, `STATEMENT`, `CHALLENGE`, `OBSERVATION`, `TERSE`, `TIMEBOXED`; the prompt asks for an even spread across all six so the app can rotate shapes between consecutive notifications. `modes` lists the supported modes the text was written for, or is empty for a mode-neutral message; the prompt asks for at least half of the batch to be neutral and the rest spread across the supported modes, and a variant tagged with a mode outside `supportedModes` is dropped from the batch. When `actionUrl` is present, the Android client renders a "Watch" action button on the notification that opens the URL.
