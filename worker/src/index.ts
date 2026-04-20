import { Hono } from 'hono'
import type { Env } from './types'
import { authMiddleware } from './middleware/auth'
import { rateLimitMiddleware } from './middleware/rateLimit'
import { spendGate } from './middleware/spendGate'
import { healthHandler } from './routes/health'
import { generateBatchHandler } from './routes/generateBatch'
import { habitFieldsHandler } from './routes/habitFields'
import { previewHandler } from './routes/preview'

const app = new Hono<{ Bindings: Env }>()

// Public — no auth
app.get('/v1/health', healthHandler)

// Shared protection for all AI generation routes
for (const path of ['/v1/generate/*', '/v1/habit-fields', '/v1/preview']) {
  app.use(path, rateLimitMiddleware, authMiddleware, spendGate)
}

app.post('/v1/generate/batch', generateBatchHandler)
app.post('/v1/habit-fields', habitFieldsHandler)
app.post('/v1/preview', previewHandler)

export default app
