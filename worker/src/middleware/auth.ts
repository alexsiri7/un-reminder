import type { MiddlewareHandler } from 'hono'
import type { Env } from '../types'
import { timingSafeEqual } from '../lib/timing'

export const authMiddleware: MiddlewareHandler<{ Bindings: Env }> = async (c, next) => {
  const provided = c.req.header('X-UR-Secret') ?? ''
  if (!timingSafeEqual(provided, c.env.UR_SECRET)) {
    return c.json({ error: 'Unauthorized' }, 401)
  }
  await next()
}
