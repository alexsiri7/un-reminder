import * as Sentry from '@sentry/cloudflare'
import type { MiddlewareHandler } from 'hono'
import type { Env } from '../types'
import { verifyToken } from '../lib/tokens'

const BEARER_PREFIX = 'Bearer '

export const authMiddleware: MiddlewareHandler<{ Bindings: Env }> = async (c, next) => {
  const header = c.req.header('Authorization') ?? ''
  const token = header.startsWith(BEARER_PREFIX) ? header.slice(BEARER_PREFIX.length).trim() : ''

  let identity
  try {
    identity = await verifyToken(c.env.UR_TOKENS, token)
  } catch (err) {
    console.error('[auth] token store unavailable', err)
    return c.json({ error: 'Auth store unavailable' }, 503)
  }
  if (identity === null) {
    return c.json({ error: 'Unauthorized' }, 401)
  }

  Sentry.setTag('token_id', identity.id)
  Sentry.setTag('token_label', identity.label)
  console.log('[auth] authenticated', { id: identity.id, label: identity.label })
  await next()
}
