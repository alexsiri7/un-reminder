import { serviceAccountAccessToken } from './googleAuth'

/** Mirrors `INTEGRITY_HEADER` in app/.../service/worker/RequestyProxyClient.kt; worker/test/fixtures/integrity-wire.txt pins both. */
export const INTEGRITY_HEADER = 'X-Play-Integrity-Token'
export const PACKAGE_NAME = 'net.interstellarai.unreminder'
export const MAX_TOKEN_AGE_MS = 10 * 60 * 1000

const DECODE_URL = `https://playintegrity.googleapis.com/v1/${PACKAGE_NAME}:decodeIntegrityToken`
const SCOPE = 'https://www.googleapis.com/auth/playintegrity'

export type IntegrityFailure =
  | 'missing'
  | 'invalid'
  | 'hash-mismatch'
  | 'stale'
  | 'unrecognized-app'
  | 'unlicensed'

/** The `tokenPayloadExternal` half of Google's decode response; every field is optional on the wire. */
export interface TokenPayload {
  requestDetails?: { requestPackageName?: string; requestHash?: string; timestampMillis?: string }
  appIntegrity?: { appRecognitionVerdict?: string; packageName?: string; versionCode?: string }
  deviceIntegrity?: { deviceRecognitionVerdict?: string[] }
  accountDetails?: { appLicensingVerdict?: string }
}

export class IntegrityDecodeError extends Error {
  constructor(
    readonly kind: 'invalid-token' | 'misconfigured' | 'unavailable',
    message: string,
  ) {
    super(message)
    this.name = 'IntegrityDecodeError'
  }
}

/** Same hex formatting as `hashToken`; the app hashes its request body the same way. */
export async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('')
}

/**
 * Decodes a standard-request token on Google's servers. Exactly one decode per token: Google
 * clears the verdicts of a token decoded twice, so a retry here could only ever fail.
 */
export async function decodeIntegrityToken(
  saKeyJson: string,
  token: string,
  fetchImpl: typeof fetch = fetch,
): Promise<TokenPayload> {
  let accessToken: string
  try {
    accessToken = await serviceAccountAccessToken(saKeyJson, SCOPE, fetchImpl)
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err)
    const kind = /^Google token exchange 4\d\d/.test(message) || message.startsWith('UR_PLAY_INTEGRITY_SA_KEY')
      ? 'misconfigured'
      : 'unavailable'
    throw new IntegrityDecodeError(kind, message)
  }

  let res: Response
  let json: { tokenPayloadExternal?: TokenPayload }
  try {
    res = await fetchImpl(DECODE_URL, {
      method: 'POST',
      headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ integrityToken: token }),
    })
    if (res.status === 400) throw new IntegrityDecodeError('invalid-token', `decode rejected the token: ${await res.text()}`)
    if (res.status === 401 || res.status === 403) {
      throw new IntegrityDecodeError('misconfigured', `decode ${res.status}: ${await res.text()}`)
    }
    if (!res.ok) throw new IntegrityDecodeError('unavailable', `decode ${res.status}: ${await res.text()}`)
    json = (await res.json()) as { tokenPayloadExternal?: TokenPayload }
  } catch (err) {
    if (err instanceof IntegrityDecodeError) throw err
    throw new IntegrityDecodeError('unavailable', err instanceof Error ? err.message : String(err))
  }
  if (typeof json.tokenPayloadExternal !== 'object' || json.tokenPayloadExternal === null) {
    throw new IntegrityDecodeError('unavailable', 'decode response carried no tokenPayloadExternal')
  }
  return json.tokenPayloadExternal
}

/**
 * The gate itself. App recognition and licensing are hard rejects; `deviceIntegrity` is
 * deliberately not consulted (worker/README.md, "Play Integrity"): a device verdict would lock
 * out friends on custom ROMs for no gain at this scale.
 */
export function evaluateVerdict(
  payload: TokenPayload,
  expected: { requestHash: string; nowMillis: number },
): IntegrityFailure | null {
  const details = payload.requestDetails ?? {}
  if (details.requestHash !== expected.requestHash) return 'hash-mismatch'
  const issued = Number(details.timestampMillis)
  if (!Number.isFinite(issued) || Math.abs(expected.nowMillis - issued) > MAX_TOKEN_AGE_MS) return 'stale'
  if (payload.appIntegrity?.appRecognitionVerdict !== 'PLAY_RECOGNIZED') return 'unrecognized-app'
  if (payload.accountDetails?.appLicensingVerdict !== 'LICENSED') return 'unlicensed'
  return null
}
