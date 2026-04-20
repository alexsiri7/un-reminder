import type { MiddlewareHandler } from 'hono'
import type { Env } from '../types'
import { timingSafeEqual } from '../lib/timing'

export const authMiddleware: MiddlewareHandler<{ Bindings: Env }> = async (c, next) => {
  const expected = c.env.UR_SECRET
  if (!expected) {
    console.error('[auth] UR_SECRET is not configured — blocking request')
    return c.json({ error: 'Service misconfigured' }, 503)
  }
  const provided = c.req.header('X-UR-Secret') ?? ''
  if (!provided || !timingSafeEqual(provided, expected)) {
    return c.json({ error: 'Unauthorized' }, 401)
  }
  await next()
}
