import * as Sentry from '@sentry/cloudflare'
import type { Context } from 'hono'
import { REGISTRATION_CAP_ERROR, type Env, type RegisterResponse } from '../types'
import { INTEGRITY_HEADER } from '../lib/integrity'
import { countRegistration, getRegistrationsToday } from '../lib/registrations'
import { createTokenRecord, mintToken, tokenKey } from '../lib/tokens'
import { verifyIntegrity } from '../middleware/integrity'

const MAX_DEVICE_LABEL_LENGTH = 40

/** Control and format characters removed, whitespace runs collapsed, capped; null when nothing is left. */
function sanitizeDeviceLabel(raw: unknown): string | null {
  if (typeof raw !== 'string') return null
  const label = Array.from(raw.replace(/[\p{Cc}\p{Cf}]/gu, ' ').replace(/\s+/g, ' ').trim())
    .slice(0, MAX_DEVICE_LABEL_LENGTH)
    .join('')
    .trim()
  return label === '' ? null : label
}

function parseDeviceLabel(body: string): string | null {
  try {
    const parsed: unknown = JSON.parse(body)
    if (typeof parsed !== 'object' || parsed === null || !('deviceLabel' in parsed)) return null
    return sanitizeDeviceLabel(parsed.deviceLabel)
  } catch {
    return null
  }
}

function reportRejection(reason: string, extra: Record<string, unknown> = {}) {
  console.warn('[register] rejected', { reason, ...extra })
  Sentry.captureMessage('registration rejected', { level: 'warning', tags: { component: 'register', reason } })
}

/**
 * The auth bootstrap: a Play-verified install trades an integrity token bound to this request's
 * body for its own per-user token. Unauthenticated by design, so it fails closed whenever Google
 * or the registration counter cannot be read, and the daily ceiling is checked before the decode
 * so an exhausted day costs no decode quota.
 */
export async function registerHandler(c: Context<{ Bindings: Env }>) {
  const maxPerDay = parseInt(c.env.UR_MAX_REGISTRATIONS_PER_DAY, 10)
  if (isNaN(maxPerDay)) {
    console.error('[register] UR_MAX_REGISTRATIONS_PER_DAY missing or invalid — blocking registration as fail-safe')
    return c.json({ error: 'Service misconfigured' }, 503)
  }

  const body = await c.req.text()
  const deviceLabel = parseDeviceLabel(body)
  if (deviceLabel === null) {
    reportRejection('bad-request')
    return c.json({ error: 'Invalid request: deviceLabel must be a non-empty string' }, 400)
  }
  const label = `self:${deviceLabel}`

  let registeredToday
  try {
    registeredToday = await getRegistrationsToday(c.env.UR_SPEND)
  } catch (err) {
    console.error('[register] registration counter unavailable', err)
    return c.json({ error: 'Registration unavailable' }, 503)
  }
  if (registeredToday >= maxPerDay) {
    reportRejection('daily-cap', { label, registeredToday, maxPerDay })
    return c.json({ error: REGISTRATION_CAP_ERROR }, 429)
  }

  const integrityToken = c.req.header(INTEGRITY_HEADER)?.trim() ?? ''
  const rejection = await verifyIntegrity(c.env.UR_PLAY_INTEGRITY_SA_KEY, integrityToken, body, { label })
  if (rejection !== null) {
    reportRejection(rejection.reason, { label })
    return c.json(rejection.body, rejection.status)
  }

  const { id, token } = mintToken()
  try {
    await c.env.UR_TOKENS.put(tokenKey(id), JSON.stringify(await createTokenRecord(token, label, new Date())))
  } catch (err) {
    console.error('[register] could not store the token', err)
    return c.json({ error: 'Registration unavailable' }, 503)
  }
  try {
    await countRegistration(c.env.UR_SPEND)
  } catch (err) {
    console.error('[register] could not count the registration', err, { id })
    Sentry.captureException(err instanceof Error ? err : new Error(String(err)), {
      tags: { component: 'register', failure: 'count-registration', token_id: id },
    })
  }

  console.log('[register] registered', { id, label })
  Sentry.captureMessage('registration', { level: 'info', tags: { component: 'register', token_id: id, token_label: label } })
  const response: RegisterResponse = { token, id }
  return c.json(response)
}
