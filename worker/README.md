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
wrangler secret put UR_REQUESTY_KEY                 # Requesty.ai API key
wrangler secret put UR_PLAY_INTEGRITY_SA_KEY < key.json   # service-account key, see "Play Integrity" below
```

### 3. Tokens

Every user of the app holds their own token, which they paste into the app's Cloud AI settings.
The Worker stores only a salted SHA-256 hash of it, so the token is printed exactly once when
minted; keep it or mint another.

```bash
npm run tokens -- mint --label <name>   # prints ur1_<id>_<secret> once and stores its hash
npm run tokens -- mint --label <name> --integrity-exempt   # same, but skips the Play Integrity gate
npm run tokens -- mint --label <name> --daily-cap-cents 50 --monthly-cap-cents 500   # own spend caps
npm run tokens -- disable <id>          # revoke: the token answers 401 from the next request
npm run tokens -- enable <id>
npm run tokens -- caps <id> --daily-cap-cents 50   # replace the token's cap overrides; omitted ⇒ default
npx wrangler kv key list --binding UR_TOKENS --remote   # ids of every minted token
npx wrangler kv key list --binding UR_SPEND --remote --prefix user:<id>:   # one token's spend counters
```

`<id>` is the 16-hex-character middle part of the token; the app shows the stored token's
`ur1_<id>` prefix so a user can tell you which one to revoke without revealing the secret.

`--integrity-exempt` is for debug builds and sideloaded APKs, which Play never recognises
(see "Play Integrity" below). The flag is fixed at mint time; to change it, mint a new token.

Each token spends against its own daily and monthly counters, capped by `UR_USER_DAILY_CAP_CENTS` /
`UR_USER_MONTHLY_CAP_CENTS` unless the record carries its own `--daily-cap-cents` /
`--monthly-cap-cents`. Unlike the exemption, caps can be changed later: `caps <id>` replaces the
token's overrides with exactly the flags given, so `caps <id>` alone puts it back on the defaults.
A record whose cap is not a positive integer is malformed and answers 401 until fixed.

### 4. Play Integrity

Every request to a generation route must also carry `X-Play-Integrity-Token`, a Play Integrity
*standard* token bound to the SHA-256 of the request body, unless the bearer token is
integrity-exempt. The Worker decodes the token on Google's servers, so it needs a service
account with access to the Play Integrity API of the Cloud project linked to the app.

Owner steps, in order:

1. Play Console → the app → *Test and release* → *App integrity* → *Play Integrity API* →
   *Link a Cloud project*. Pick the Cloud project that already holds the Play-publishing service
   account so there is only one project to look after.
2. In that Cloud project confirm the *Google Play Integrity API* is enabled (linking normally
   enables it).
3. Create a **new** service account with no roles (e.g. `un-reminder-worker`), create a JSON
   key for it, and store it as the Worker secret:

   ```bash
   npx wrangler secret put UR_PLAY_INTEGRITY_SA_KEY < key.json
   rm key.json
   ```

   Do not reuse the publishing account's key: a leaked Worker secret must not be able to publish
   to Play. If the first decode answers `403 PERMISSION_DENIED`, grant the account
   `roles/serviceusage.serviceUsageConsumer` on the project.
4. Copy the Cloud project **number** (not the id) into the `PLAY_CLOUD_PROJECT_NUMBER` GitHub
   secret so release builds bake it into `BuildConfig`.
5. Mint an exempt token for your own debug builds: `npm run tokens -- mint --label alex-dev --integrity-exempt`.

Do these before merging a Worker that enforces the gate: with the secret unset every
non-exempt request answers `503`.

**What is enforced.** The gate runs after the bearer token is verified and before the spend
gate, so an unauthenticated request never costs a decode and a rejected build never reads spend.

| Verdict | Outcome |
|---------|---------|
| Header missing on a non-exempt token | `403 { "reason": "missing" }` — no Google call |
| Google cannot decode the token (400) | `403 { "reason": "invalid" }` |
| `requestDetails.requestHash` ≠ SHA-256 of the body received | `403 { "reason": "hash-mismatch" }` |
| `requestDetails.timestampMillis` more than 10 minutes from now | `403 { "reason": "stale" }` |
| `appIntegrity.appRecognitionVerdict` ≠ `PLAY_RECOGNIZED` | `403 { "reason": "unrecognized-app" }` |
| `accountDetails.appLicensingVerdict` ≠ `LICENSED` | `403 { "reason": "unlicensed" }` |
| `deviceIntegrity.deviceRecognitionVerdict` — anything, including empty | **Logged only**, never blocks |
| `UR_PLAY_INTEGRITY_SA_KEY` unset, or Google refuses the service account | `503 { "error": "Service misconfigured" }` |
| Google answers 429 or 5xx, or is unreachable | `503 { "error": "Integrity check unavailable" }` |

Every `403` body is `{ "error": "Play Integrity check failed", "reason": <reason> }`; `401`
stays "who are you" (bearer token), `403` is "you, but not from a Play build".

Device integrity is deliberately not a gate: blocking on it would lock out friends on custom
ROMs or unlocked bootloaders and buys nothing at this scale. The verdict is logged with the
token label and set as the `device_integrity` Sentry tag so a pattern can still be seen.

**Degrade mode.** The Worker is the sole enforcer and fails closed. When Google cannot be asked
it answers `503` after exactly one decode attempt (Google clears the verdicts of a token
decoded twice, so retrying the same token could only fail); the app already treats `5xx` as
"try again later", pools are pre-generated, and both the daily quotas (10 000 token requests
and 10 000 decodes per Cloud project) and outages end on their own, so nothing is permanent.
Failing open here was rejected: the decode runs for any presented token string, so a stolen
bearer token could exhaust the decode quota with garbage and then walk through.

On the device, the app always sends the request and attaches the token when it can get one.
An install that cannot obtain a token — no Play Services, an outdated Play Store, a build with
no `PLAY_CLOUD_PROJECT_NUMBER`, the sideload APK from `release.yml`, a debug build — sends no
header and gets `403 missing`; the app retries only when the local failure was transient. Such
an install cannot generate without an integrity-exempt token, and that is the gate working as
intended.

### 5. Environment variables

Configured in `wrangler.toml` under `[vars]`:

| Variable | Default | Description |
|----------|---------|-------------|
| `UR_MODEL` | `google/gemini-3.6-flash` | Model to use via Requesty |
| `UR_DAILY_CAP_CENTS` | `50` | Max daily spend in cents across every token — the backstop on the bill |
| `UR_MONTHLY_CAP_CENTS` | `500` | Max monthly spend in cents across every token |
| `UR_USER_DAILY_CAP_CENTS` | `20` | Max daily spend in cents per token, unless its record overrides it (`npm run tokens -- caps`) |
| `UR_USER_MONTHLY_CAP_CENTS` | `200` | Max monthly spend in cents per token, unless its record overrides it |
| `UR_MAX_REGISTRATIONS_PER_DAY` | `20` | Max tokens `POST /v1/register` mints per UTC day across every install; a value that is not a number makes the route answer 503 |
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

### 6. Rate limiting (optional)

Configure rate limiting rules at the Cloudflare zone dashboard level (not in Worker code).

## API

### `GET /v1/health`

Returns worker status, the service-wide daily and monthly spend and caps (no per-token figures:
the route is unauthenticated) and the deployed `generationVersion`.

### `POST /v1/register`

Mints a per-user token for a Play-verified install, so the app can obtain its own instead of
having one minted and pasted in. Needs no bearer token — it is how bearer tokens are obtained —
but requires `X-Play-Integrity-Token` bound to the SHA-256 of the request body, checked exactly as
on the generation routes (the verdict table under "Play Integrity" above, including the fail-closed
`503`s). There is no exemption: debug builds and sideloaded APKs keep using `npm run tokens -- mint`.

**Request body:**

```json
{ "deviceLabel": "Pixel 8" }
```

`deviceLabel` is a short, user-visible name for the install, e.g. the device model. Control
characters are dropped, whitespace collapsed and the result cut to 40 characters; one that is
missing, not a string or empty after that answers `400`.

**Response:**

```json
{ "token": "ur1_<id>_<secret>", "id": "<id>" }
```

The token is returned this once. Its record is labelled `self:<deviceLabel>`, sits on the default
spend caps, is not integrity-exempt and is revoked like any other (`npm run tokens -- disable <id>`).

Abuse bounds: the route sits behind `REQUEST_LIMITER`, and at most `UR_MAX_REGISTRATIONS_PER_DAY`
tokens are minted per UTC day across every install, counted in `UR_SPEND` under
`registrations:day:<date>`. Past that it answers `429 { "error": "Daily registration limit reached" }`
before any decode — distinct from the limiter's `429 { "error": "Rate limit exceeded" }`. The
literals the app matches on are pinned in `test/fixtures/register-wire.txt`. If the counter cannot be
read or written, or the token cannot be stored, it answers `503` and mints nothing. Every registration
and every rejection is reported to Sentry (`component: register`) with its reason; the token never is.

An integrity token spent here cannot be replayed elsewhere: it is bound to this body's hash, and
Google clears the verdicts of a token decoded twice.

### `POST /v1/generate/batch`

Generates notification text variants. Requires `Authorization: Bearer <token>` and, unless the
token is integrity-exempt, `X-Play-Integrity-Token` (see "Play Integrity" above).

Answers `402` once a spend cap is reached, with `{ "error", "capType", "capScope" }`: `capType` is
`daily` or `monthly`; `capScope` is `user` when the caller's own counter ran out and `global` when
the service-wide one did. A caller over both hears `user`. The literals are pinned in
`test/fixtures/spend-wire.txt`. `/v1/habit-fields` answers the same way.

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
