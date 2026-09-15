import { describe, it, expect, beforeAll, vi } from 'vitest'
import {
  IntegrityDecodeError,
  MAX_TOKEN_AGE_MS,
  decodeIntegrityToken,
  evaluateVerdict,
  sha256Hex,
  type TokenPayload,
} from './integrity'
import { hashToken } from './tokens'
import { generateTestServiceAccount } from '../../test/serviceAccount'

const NOW = Date.parse('2026-09-15T12:00:00Z')
const HASH = 'a'.repeat(64)

function goodPayload(overrides: Partial<TokenPayload> = {}): TokenPayload {
  return {
    requestDetails: { requestPackageName: 'net.interstellarai.unreminder', requestHash: HASH, timestampMillis: String(NOW - 5_000) },
    appIntegrity: { appRecognitionVerdict: 'PLAY_RECOGNIZED', packageName: 'net.interstellarai.unreminder', versionCode: '42' },
    deviceIntegrity: { deviceRecognitionVerdict: ['MEETS_DEVICE_INTEGRITY'] },
    accountDetails: { appLicensingVerdict: 'LICENSED' },
    ...overrides,
  }
}

describe('sha256Hex', () => {
  it('formats like hashToken', async () => {
    expect(await sha256Hex('salt' + 'x')).toBe(await hashToken('salt', 'x'))
    expect(await sha256Hex('')).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
  })
})

describe('evaluateVerdict', () => {
  const expected = { requestHash: HASH, nowMillis: NOW }

  it('passes a Play-recognised, licensed, fresh token bound to this body', () => {
    expect(evaluateVerdict(goodPayload(), expected)).toBeNull()
  })

  it('passes with an empty device verdict — device integrity never blocks', () => {
    expect(evaluateVerdict(goodPayload({ deviceIntegrity: { deviceRecognitionVerdict: [] } }), expected)).toBeNull()
    expect(evaluateVerdict(goodPayload({ deviceIntegrity: undefined }), expected)).toBeNull()
  })

  it.each<[string, TokenPayload, string]>([
    ['a token bound to another body', goodPayload({ requestDetails: { requestHash: 'b'.repeat(64), timestampMillis: String(NOW) } }), 'hash-mismatch'],
    ['a token without request details', goodPayload({ requestDetails: undefined }), 'hash-mismatch'],
    ['a token older than the window', goodPayload({ requestDetails: { requestHash: HASH, timestampMillis: String(NOW - 11 * 60_000) } }), 'stale'],
    ['a token from the future', goodPayload({ requestDetails: { requestHash: HASH, timestampMillis: String(NOW + 11 * 60_000) } }), 'stale'],
    ['a token with no timestamp', goodPayload({ requestDetails: { requestHash: HASH } }), 'stale'],
    ['an unrecognised build', goodPayload({ appIntegrity: { appRecognitionVerdict: 'UNRECOGNIZED_VERSION' } }), 'unrecognized-app'],
    ['no app verdict at all', goodPayload({ appIntegrity: undefined }), 'unrecognized-app'],
    ['an unlicensed install', goodPayload({ accountDetails: { appLicensingVerdict: 'UNLICENSED' } }), 'unlicensed'],
    ['an unevaluated licence', goodPayload({ accountDetails: { appLicensingVerdict: 'UNEVALUATED' } }), 'unlicensed'],
  ])('rejects %s', (_name, payload, reason) => {
    expect(evaluateVerdict(payload, expected)).toBe(reason)
  })

  it('accepts a token nine minutes old and rejects one eleven minutes old', () => {
    const at = (ageMs: number) => goodPayload({ requestDetails: { requestHash: HASH, timestampMillis: String(NOW - ageMs) } })
    expect(evaluateVerdict(at(9 * 60_000), expected)).toBeNull()
    expect(evaluateVerdict(at(MAX_TOKEN_AGE_MS), expected)).toBeNull()
    expect(evaluateVerdict(at(11 * 60_000), expected)).toBe('stale')
  })
})

describe('decodeIntegrityToken', () => {
  let keyJson: string

  beforeAll(async () => {
    keyJson = (await generateTestServiceAccount()).keyJson
  })

  function fetchQueue(...responses: Array<() => Response>) {
    let i = 0
    const impl = vi.fn(async () => (responses[i++] ?? (() => new Response('unexpected call', { status: 500 })))())
    return impl as unknown as typeof fetch & { mock: { calls: Array<[string, RequestInit]> } }
  }
  const exchangeOk = () => Response.json({ access_token: 'ya29.test' })

  it('posts the token to the decode endpoint with the exchanged bearer and returns the payload', async () => {
    const payload = goodPayload()
    const fetchImpl = fetchQueue(exchangeOk, () => Response.json({ tokenPayloadExternal: payload }))

    expect(await decodeIntegrityToken(keyJson, 'the-token', fetchImpl)).toEqual(payload)

    expect(fetchImpl.mock.calls).toHaveLength(2)
    const [url, init] = fetchImpl.mock.calls[1]
    expect(url).toBe('https://playintegrity.googleapis.com/v1/net.interstellarai.unreminder:decodeIntegrityToken')
    expect(init.method).toBe('POST')
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer ya29.test')
    expect(JSON.parse(init.body as string)).toEqual({ integrityToken: 'the-token' })
  })

  it.each([
    [400, 'invalid-token'],
    [401, 'misconfigured'],
    [403, 'misconfigured'],
    [429, 'unavailable'],
    [503, 'unavailable'],
  ])('maps a decode %s to %s after exactly one decode call', async (status, kind) => {
    const fetchImpl = fetchQueue(exchangeOk, () => new Response('nope', { status }))

    const err = await decodeIntegrityToken(keyJson, 'the-token', fetchImpl).catch((e: unknown) => e)
    expect(err).toBeInstanceOf(IntegrityDecodeError)
    expect((err as IntegrityDecodeError).kind).toBe(kind)
    expect(fetchImpl.mock.calls).toHaveLength(2)
  })

  it('reports a payload-less decode response as unavailable', async () => {
    const fetchImpl = fetchQueue(exchangeOk, () => Response.json({}))
    await expect(decodeIntegrityToken(keyJson, 'the-token', fetchImpl)).rejects.toMatchObject({ kind: 'unavailable' })
  })

  it('reports a refused token exchange as misconfigured and a failed one as unavailable', async () => {
    await expect(
      decodeIntegrityToken(keyJson, 'the-token', fetchQueue(() => new Response('invalid_grant', { status: 400 }))),
    ).rejects.toMatchObject({ kind: 'misconfigured' })
    await expect(
      decodeIntegrityToken(keyJson, 'the-token', fetchQueue(() => new Response('down', { status: 503 }))),
    ).rejects.toMatchObject({ kind: 'unavailable' })
    await expect(decodeIntegrityToken('not a key', 'the-token', fetchQueue())).rejects.toMatchObject({ kind: 'misconfigured' })
  })
})
