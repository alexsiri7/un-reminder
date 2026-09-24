// Record side of `npm run tokens`; src/lib/tokens.ts verifies tokens and mints the ones
// self-registration hands out. Both store hex SHA-256 of `salt + token` under `token:<id>`, and
// src/lib/tokens.test.ts fails if they drift. Plain JavaScript so tokens.mjs runs under `node` without a TypeScript loader.

const hex = (bytes) => Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
const randomHex = (byteLength) => hex(crypto.getRandomValues(new Uint8Array(byteLength)))

export const tokenKey = (id) => `token:${id}`

/** `ur1_<id>_<secret>`: 64 bits to look the record up by, 256 bits of secret. */
export function mintToken() {
  const id = randomHex(8)
  return { id, token: `ur1_${id}_${randomHex(32)}` }
}

export async function hashToken(salt, token) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(salt + token))
  return hex(new Uint8Array(digest))
}

/** Cap overrides are in cents; a record without one falls back to the Worker's UR_USER_*_CAP_CENTS. */
export async function createTokenRecord(token, label, now, { integrityExempt = false, dailyCapCents, monthlyCapCents } = {}) {
  const salt = randomHex(16)
  return {
    hash: await hashToken(salt, token),
    salt,
    label,
    createdAt: now.toISOString(),
    enabled: true,
    ...(integrityExempt && { integrityExempt: true }),
    ...(dailyCapCents !== undefined && { dailyCapCents }),
    ...(monthlyCapCents !== undefined && { monthlyCapCents }),
  }
}

/** The record with its cap overrides replaced by exactly [caps]; an omitted cap goes back to the Worker default. */
export function applyCaps(record, caps) {
  const { dailyCapCents: _daily, monthlyCapCents: _monthly, ...rest } = record
  return { ...rest, ...caps }
}

/** One `list` line for the record stored under `token:<id>`; the hash and salt are never shown. */
export function describeToken(id, { label, createdAt, enabled, integrityExempt, dailyCapCents, monthlyCapCents }) {
  return [
    id,
    enabled ? 'enabled ' : 'disabled',
    createdAt,
    `caps ${dailyCapCents ?? 'default'}/${monthlyCapCents ?? 'default'}`,
    integrityExempt ? 'integrity-exempt' : 'integrity-gated ',
    JSON.stringify(label),
  ].join('  ')
}
