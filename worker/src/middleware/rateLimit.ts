import type { MiddlewareHandler } from 'hono'
import type { Env } from '../types'

/**
 * Enforce the Cloudflare rate-limit binding configured in wrangler.toml.
 * Keyed per connecting IP; returns 429 when the limit is exceeded.
 */
export const rateLimitMiddleware: MiddlewareHandler<{ Bindings: Env }> = async (c, next) => {
  const { success } = await c.env.REQUEST_LIMITER.limit({
    key: c.req.header('CF-Connecting-IP') ?? 'unknown',
  })
  if (!success) {
    return c.json({ error: 'Rate limit exceeded' }, 429)
  }
  await next()
}
