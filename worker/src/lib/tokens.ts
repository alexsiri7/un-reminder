import { timingSafeEqual } from './timing'

/**
 * `ur1_<id>_<secret>`: a 64-bit hex id the record is looked up by, then a 256-bit hex secret
 * that exists in plaintext only on the user's device. Mirrors `WorkerToken.PATTERN` in
 * app/src/main/java/net/interstellarai/unreminder/service/worker/WorkerToken.kt and the minting
 * side in scripts/tokenRecord.mjs — keep the three in sync.
 */
export const TOKEN_PATTERN = /^ur1_([0-9a-f]{16})_[0-9a-f]{64}$/

/** KV value under `token:<id>`. The plaintext token is never stored. */
export interface TokenRecord {
  hash: string
  salt: string
  label: string
  createdAt: string
  enabled: boolean
  /** Skips the Play Integrity gate; minted for debug builds, absent on every other record. */
  integrityExempt?: boolean
  /** Spend caps in cents, positive integers; absent ⇒ UR_USER_DAILY_CAP_CENTS / UR_USER_MONTHLY_CAP_CENTS. */
  dailyCapCents?: number
  monthlyCapCents?: number
}

export interface TokenIdentity {
  id: string
  label: string
  integrityExempt: boolean
  dailyCapCents?: number
  monthlyCapCents?: number
}

export function tokenKey(id: string): string {
  return `token:${id}`
}

export function parseTokenId(token: string): string | null {
  return TOKEN_PATTERN.exec(token)?.[1] ?? null
}

/** Hex SHA-256 of `salt + token`; scripts/tokenRecord.mjs writes records with the same formula. */
export async function hashToken(salt: string, token: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(salt + token))
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('')
}

const isCapCents = (value: unknown) => value === undefined || (Number.isInteger(value) && (value as number) > 0)

function isTokenRecord(value: unknown): value is TokenRecord {
  if (typeof value !== 'object' || value === null) return false
  const record = value as Record<string, unknown>
  return (
    typeof record.hash === 'string' &&
    typeof record.salt === 'string' &&
    typeof record.label === 'string' &&
    typeof record.createdAt === 'string' &&
    typeof record.enabled === 'boolean' &&
    (record.integrityExempt === undefined || typeof record.integrityExempt === 'boolean') &&
    isCapCents(record.dailyCapCents) &&
    isCapCents(record.monthlyCapCents)
  )
}

/**
 * Resolves a presented token to its identity, or null when it is malformed, unknown, wrong or
 * disabled. A KV failure propagates so the caller can fail closed rather than open.
 */
export async function verifyToken(kv: Pick<KVNamespace, 'get'>, token: string): Promise<TokenIdentity | null> {
  const id = parseTokenId(token)
  if (id === null) return null
  const record: unknown = await kv.get(tokenKey(id), 'json')
  if (record === null) return null
  if (!isTokenRecord(record)) {
    console.error('[auth] malformed token record', { id })
    return null
  }
  if (!timingSafeEqual(await hashToken(record.salt, token), record.hash)) return null
  if (!record.enabled) {
    console.warn('[auth] disabled token', { id, label: record.label })
    return null
  }
  return {
    id,
    label: record.label,
    integrityExempt: record.integrityExempt === true,
    ...(record.dailyCapCents !== undefined && { dailyCapCents: record.dailyCapCents }),
    ...(record.monthlyCapCents !== undefined && { monthlyCapCents: record.monthlyCapCents }),
  }
}
