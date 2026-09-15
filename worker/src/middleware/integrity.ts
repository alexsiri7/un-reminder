import * as Sentry from '@sentry/cloudflare'
import type { MiddlewareHandler } from 'hono'
import type { AppEnv } from '../types'
import {
  INTEGRITY_HEADER,
  IntegrityDecodeError,
  decodeIntegrityToken,
  evaluateVerdict,
  sha256Hex,
  type IntegrityFailure,
} from '../lib/integrity'

/**
 * Second gate behind authMiddleware: the request must carry a Play Integrity token bound to
 * its body, decoded by Google and vouching for a Play-recognised, licensed install. Runs
 * before the spend gate so a rejected build never reads spend. Fails closed when Google
 * cannot be asked (missing secret, 429/5xx): a stolen user token must not be able to burn
 * the decode quota and then walk through an open door.
 */
export const integrityMiddleware: MiddlewareHandler<AppEnv> = async (c, next) => {
  const { id, label, integrityExempt } = c.get('tokenIdentity')
  const reject = (reason: IntegrityFailure, extra: Record<string, unknown> = {}) => {
    console.warn('[integrity] rejected', { id, label, reason, ...extra })
    Sentry.setTag('integrity', reason)
    return c.json({ error: 'Play Integrity check failed', reason }, 403)
  }

  if (integrityExempt) {
    Sentry.setTag('integrity', 'exempt')
    console.log('[integrity] exempt token', { id, label })
    await next()
    return
  }

  const token = c.req.header(INTEGRITY_HEADER)?.trim() ?? ''
  if (token === '') return reject('missing')

  const saKey = c.env.UR_PLAY_INTEGRITY_SA_KEY
  if (!saKey) {
    console.error('[integrity] UR_PLAY_INTEGRITY_SA_KEY not set — blocking request as fail-safe')
    Sentry.captureMessage('UR_PLAY_INTEGRITY_SA_KEY not set', { tags: { component: 'integrity' } })
    return c.json({ error: 'Service misconfigured' }, 503)
  }

  // Hono caches the body, so the route handler's c.req.json() still works after this read.
  const requestHash = await sha256Hex(await c.req.text())

  let payload
  try {
    payload = await decodeIntegrityToken(saKey, token)
  } catch (err) {
    const kind = err instanceof IntegrityDecodeError ? err.kind : 'unavailable'
    if (kind === 'invalid-token') return reject('invalid', { detail: (err as Error).message })
    console.error(`[integrity] decode ${kind}`, err)
    Sentry.captureException(err instanceof Error ? err : new Error(String(err)), {
      tags: { component: 'integrity', failure: kind },
    })
    return c.json({ error: kind === 'misconfigured' ? 'Service misconfigured' : 'Integrity check unavailable' }, 503)
  }

  const failure = evaluateVerdict(payload, { requestHash, nowMillis: Date.now() })
  if (failure !== null) {
    return reject(failure, {
      appRecognitionVerdict: payload.appIntegrity?.appRecognitionVerdict,
      appLicensingVerdict: payload.accountDetails?.appLicensingVerdict,
    })
  }

  const device = payload.deviceIntegrity?.deviceRecognitionVerdict ?? []
  Sentry.setTag('integrity', 'verified')
  Sentry.setTag('device_integrity', device.join(',') || 'none')
  console.log('[integrity] verified', { id, label, device })
  await next()
}
