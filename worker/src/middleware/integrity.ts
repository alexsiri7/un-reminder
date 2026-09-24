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

/** Why a request did not pass: a verdict-level 403, or a 503 because Google could not be asked. */
export type IntegrityRejection =
  | { status: 403; reason: IntegrityFailure; body: { error: string; reason: IntegrityFailure } }
  | { status: 503; reason: 'misconfigured' | 'unavailable'; body: { error: string } }

/**
 * Decodes [token] with Google and checks it vouches for a Play-recognised, licensed install that
 * sent exactly [body]; null when it does. Fails closed when Google cannot be asked (missing
 * secret, 429/5xx). [logFields] identify the caller in every log line.
 */
export async function verifyIntegrity(
  saKey: string | undefined,
  token: string,
  body: string,
  logFields: Record<string, unknown>,
): Promise<IntegrityRejection | null> {
  const reject = (reason: IntegrityFailure, extra: Record<string, unknown> = {}): IntegrityRejection => {
    console.warn('[integrity] rejected', { ...logFields, reason, ...extra })
    Sentry.setTag('integrity', reason)
    // The app matches this exact `error` string (RequestyProxyClient.kt); test/fixtures/integrity-wire.txt pins both.
    return { status: 403, reason, body: { error: 'Play Integrity check failed', reason } }
  }
  const unavailable = (reason: 'misconfigured' | 'unavailable'): IntegrityRejection => ({
    status: 503,
    reason,
    body: { error: reason === 'misconfigured' ? 'Service misconfigured' : 'Integrity check unavailable' },
  })

  if (token === '') return reject('missing')

  if (!saKey) {
    console.error('[integrity] UR_PLAY_INTEGRITY_SA_KEY not set — blocking request as fail-safe')
    Sentry.captureMessage('UR_PLAY_INTEGRITY_SA_KEY not set', { tags: { component: 'integrity' } })
    return unavailable('misconfigured')
  }

  const requestHash = await sha256Hex(body)

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
    return unavailable(kind)
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
  console.log('[integrity] verified', { ...logFields, device })
  return null
}

/**
 * Second gate behind authMiddleware: the request must carry a Play Integrity token bound to
 * its body, decoded by Google and vouching for a Play-recognised, licensed install. Runs
 * before the spend gate so a rejected build never reads spend. Fails closed when Google
 * cannot be asked: a stolen user token must not be able to burn the decode quota and then
 * walk through an open door.
 */
export const integrityMiddleware: MiddlewareHandler<AppEnv> = async (c, next) => {
  const { id, label, integrityExempt } = c.get('tokenIdentity')

  if (integrityExempt) {
    Sentry.setTag('integrity', 'exempt')
    console.log('[integrity] exempt token', { id, label })
    await next()
    return
  }

  const token = c.req.header(INTEGRITY_HEADER)?.trim() ?? ''
  // Hono caches the body, so the route handler's c.req.json() still works after this read.
  const body = await c.req.text()
  const rejection = await verifyIntegrity(c.env.UR_PLAY_INTEGRITY_SA_KEY, token, body, { id, label })
  if (rejection !== null) return c.json(rejection.body, rejection.status)
  await next()
}
