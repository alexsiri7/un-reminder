import { describe, it, expect, beforeAll, vi } from 'vitest'
import { serviceAccountAccessToken } from './googleAuth'
import { generateTestServiceAccount, verifyJwt, type TestServiceAccount } from '../../test/serviceAccount'

const SCOPE = 'https://www.googleapis.com/auth/playintegrity'

describe('serviceAccountAccessToken', () => {
  let account: TestServiceAccount

  beforeAll(async () => {
    account = await generateTestServiceAccount()
  })

  it('posts a signed JWT assertion to the token endpoint and returns the access token', async () => {
    const fetchImpl = vi.fn(async () => Response.json({ access_token: 'ya29.test', expires_in: 3599 }))

    const token = await serviceAccountAccessToken(account.keyJson, SCOPE, fetchImpl as unknown as typeof fetch)

    expect(token).toBe('ya29.test')
    expect(fetchImpl).toHaveBeenCalledTimes(1)
    const [url, init] = fetchImpl.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('https://oauth2.googleapis.com/token')
    expect(init.method).toBe('POST')
    const form = new URLSearchParams(init.body as string)
    expect(form.get('grant_type')).toBe('urn:ietf:params:oauth:grant-type:jwt-bearer')

    const { header, claims, valid } = await verifyJwt(form.get('assertion')!, account.publicKey)
    expect(valid).toBe(true)
    expect(header).toEqual({ alg: 'RS256', typ: 'JWT' })
    expect(claims.iss).toBe(account.clientEmail)
    expect(claims.scope).toBe(SCOPE)
    expect(claims.aud).toBe('https://oauth2.googleapis.com/token')
    expect((claims.exp as number) - (claims.iat as number)).toBe(3600)
  })

  it('throws with the status when the exchange is refused', async () => {
    const fetchImpl = vi.fn(async () => new Response('invalid_grant', { status: 400 }))

    await expect(
      serviceAccountAccessToken(account.keyJson, SCOPE, fetchImpl as unknown as typeof fetch),
    ).rejects.toThrow('Google token exchange 400: invalid_grant')
  })

  it.each([
    ['not JSON', 'not json'],
    ['JSON without a private key', JSON.stringify({ client_email: 'x@y' })],
    ['JSON without a client email', JSON.stringify({ private_key: '-----BEGIN PRIVATE KEY-----' })],
  ])('rejects %s without calling Google', async (_name, keyJson) => {
    const fetchImpl = vi.fn()

    await expect(serviceAccountAccessToken(keyJson, SCOPE, fetchImpl as unknown as typeof fetch)).rejects.toThrow(
      'UR_PLAY_INTEGRITY_SA_KEY is not a service-account key',
    )
    expect(fetchImpl).not.toHaveBeenCalled()
  })
})
