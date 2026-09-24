import { withSentry } from '@sentry/cloudflare'
import { Hono } from 'hono'
import type { Env } from './types'
import { authMiddleware } from './middleware/auth'
import { integrityMiddleware } from './middleware/integrity'
import { rateLimitMiddleware } from './middleware/rateLimit'
import { spendGate } from './middleware/spendGate'
import { healthHandler } from './routes/health'
import { generateBatchHandler } from './routes/generateBatch'
import { habitFieldsHandler } from './routes/habitFields'
import { registerHandler } from './routes/register'

const app = new Hono<{ Bindings: Env }>()

// Public — no auth
app.get('/v1/health', healthHandler)

// The auth bootstrap — no bearer token; the route runs its own integrity check (routes/register.ts)
app.use('/v1/register', rateLimitMiddleware)
app.post('/v1/register', registerHandler)

// Shared protection for all AI generation routes — applied in order: rate-limit → auth → integrity → spend gate
for (const path of ['/v1/generate/*', '/v1/habit-fields']) {
  app.use(path, rateLimitMiddleware, authMiddleware, integrityMiddleware, spendGate)
}

app.post('/v1/generate/batch', generateBatchHandler)
app.post('/v1/habit-fields', habitFieldsHandler)

export default withSentry(
  (env: Env) => {
    if (!env.SENTRY_DSN) console.warn('[un-reminder-worker] SENTRY_DSN not set — errors will not be reported to Sentry')
    return { dsn: env.SENTRY_DSN ?? '' }
  },
  { fetch: app.fetch }
)
