import { Hono } from 'hono'
import type { Env } from './types'
import { authMiddleware } from './middleware/auth'
import { spendGate } from './middleware/spendGate'
import { healthHandler } from './routes/health'
import { generateBatchHandler } from './routes/generateBatch'

const app = new Hono<{ Bindings: Env }>()

// Public — no auth
app.get('/v1/health', healthHandler)

// All /v1/generate/* require auth + spend gate
app.use('/v1/generate/*', authMiddleware)
app.use('/v1/generate/*', spendGate)

app.post('/v1/generate/batch', generateBatchHandler)

export default app
