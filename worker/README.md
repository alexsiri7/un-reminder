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

### 1. Create KV namespace

```bash
wrangler kv namespace create UR_SPEND
```

Copy the returned `id` into `wrangler.toml` under `[[kv_namespaces]]`.

### 2. Set secrets

```bash
wrangler secret put UR_SHARED_SECRET   # shared secret for X-UR-Secret auth
wrangler secret put UR_REQUESTY_KEY    # Requesty.ai API key
```

### 3. Environment variables

Configured in `wrangler.toml` under `[vars]`:

| Variable | Default | Description |
|----------|---------|-------------|
| `UR_MODEL` | `gemini-3-flash-preview` | Model to use via Requesty |
| `UR_DAILY_CAP_CENTS` | `50` | Max daily spend in cents |
| `UR_MONTHLY_CAP_CENTS` | `500` | Max monthly spend in cents |

### 4. Rate limiting (optional)

Configure rate limiting rules at the Cloudflare zone dashboard level (not in Worker code).

## API

### `GET /v1/health`

Returns worker status and current daily spend.

### `POST /v1/generate/batch`

Generates notification text variants. Requires `X-UR-Secret` header.

**Request body:**

```json
{
  "habitTitle": "Morning stretch",
  "habitTags": ["fitness", "morning"],
  "locationName": "Home",
  "timeOfDay": "morning",
  "n": 3
}
```

**Response:**

```json
{
  "variants": ["Time to stretch! 🧘", "Your body called", "Let's move!"]
}
```
