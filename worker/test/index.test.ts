import {
  env,
  createExecutionContext,
  waitOnExecutionContext,
} from 'cloudflare:test'
import { describe, it, expect, beforeAll, beforeEach, afterEach, vi } from 'vitest'
import app from '../src/index'
import { sha256Hex, type TokenPayload } from '../src/lib/integrity'
import { parseTokenId, tokenKey } from '../src/lib/tokens'
import { spendKeys } from '../src/lib/spend'
import { createTokenRecord } from '../scripts/tokenRecord.mjs'
import { generateTestServiceAccount } from './serviceAccount'
import { integrityWire } from './integrityWire'
import { spendWire } from './spendWire'

// A and B are integrity-exempt so the generation tests exercise only the route under test;
// C is a Play user whose requests must carry a verified integrity token.
const TOKEN_A = 'ur1_000000000000000a_' + 'a'.repeat(64)
const TOKEN_B = 'ur1_000000000000000b_' + 'b'.repeat(64)
const TOKEN_C = 'ur1_000000000000000c_' + 'c'.repeat(64)
const bearer = (token: string) => ({ Authorization: `Bearer ${token}` })

async function seedToken(
  token: string,
  label: string,
  enabled = true,
  integrityExempt = true,
  caps: { dailyCapCents?: number; monthlyCapCents?: number } = {},
) {
  const record = await createTokenRecord(token, label, new Date('2026-09-15T00:00:00Z'), { integrityExempt, ...caps })
  await env.UR_TOKENS.put(tokenKey(parseTokenId(token)!), JSON.stringify({ ...record, enabled }))
}

/** Today's UR_SPEND daily key: the Worker-wide one, or [token]'s own. */
const dailySpendKey = (token?: string) => spendKeys(token === undefined ? undefined : parseTokenId(token)!).daily

async function postBatch(e: ReturnType<typeof testEnv>, token: string) {
  const req = makeRequest('/v1/generate/batch', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...bearer(token) },
    body: validBody(1),
  })
  const ctx = createExecutionContext()
  const res = await app.fetch(req, e, ctx)
  await waitOnExecutionContext(ctx)
  return res
}

async function postHabitFields(e: ReturnType<typeof testEnv>, token: string) {
  const req = makeRequest('/v1/habit-fields', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...bearer(token) },
    body: { title: 'Meditate' },
  })
  const ctx = createExecutionContext()
  const res = await app.fetch(req, e, ctx)
  await waitOnExecutionContext(ctx)
  return res
}

type SpendCapBody = { error: string; capType: string; capScope: string }

let saKeyJson: string

const originalFetch = globalThis.fetch

function makeRequest(
  path: string,
  opts?: { method?: string; headers?: Record<string, string>; body?: unknown },
) {
  const url = `http://localhost${path}`
  const init: RequestInit = { method: opts?.method ?? 'GET' }
  if (opts?.headers) init.headers = opts.headers
  if (opts?.body) init.body = JSON.stringify(opts.body)
  return new Request(url, init)
}

function validBody(n = 3) {
  return {
    habitTitle: 'Morning stretch',
    habitTags: ['fitness', 'morning'],
    locationName: 'Home',
    timeOfDay: 'morning',
    n,
  }
}

let fetchCallIndex = 0
const fetchResponses: Array<{ status: number; body: string; headers?: Record<string, string> }> = []

function enqueueResponse(status: number, body: string, headers?: Record<string, string>) {
  fetchResponses.push({ status, body, headers })
}

function mockRequestySuccess(content: unknown) {
  enqueueResponse(
    200,
    JSON.stringify({
      choices: [{ message: { content: JSON.stringify(content) } }],
      usage: { prompt_tokens: 100, completion_tokens: 50 },
    }),
    { 'Content-Type': 'application/json' },
  )
}

function mockRequestyMalformed() {
  enqueueResponse(
    200,
    JSON.stringify({
      choices: [{ message: { content: 'this is not json' } }],
      usage: { prompt_tokens: 100, completion_tokens: 50 },
    }),
    { 'Content-Type': 'application/json' },
  )
}

/** Google's answer to a decode, vouching for a Play install that sent exactly [body]. */
async function mockIntegrityDecode(body: unknown, overrides: Partial<TokenPayload> = {}, exchangeStatus = 200) {
  enqueueResponse(exchangeStatus, JSON.stringify({ access_token: 'ya29.test' }))
  const payload: TokenPayload = {
    requestDetails: { requestPackageName: 'net.interstellarai.unreminder', requestHash: await sha256Hex(JSON.stringify(body)), timestampMillis: String(Date.now()) },
    appIntegrity: { appRecognitionVerdict: 'PLAY_RECOGNIZED', packageName: 'net.interstellarai.unreminder', versionCode: '42' },
    deviceIntegrity: { deviceRecognitionVerdict: ['MEETS_DEVICE_INTEGRITY'] },
    accountDetails: { appLicensingVerdict: 'LICENSED' },
    ...overrides,
  }
  enqueueResponse(200, JSON.stringify({ tokenPayloadExternal: payload }))
}

function testEnv() {
  return {
    ...env,
    // The real binding keys on the (absent) client IP and this file sends more than 60
    // requests a minute; the limiter is Cloudflare's, not under test here.
    REQUEST_LIMITER: { limit: async () => ({ success: true }) } as RateLimit,
    UR_REQUESTY_KEY: 'test-requesty-key',
    UR_PLAY_INTEGRITY_SA_KEY: saKeyJson,
    UR_MODEL: 'google/gemini-3.6-flash',
    UR_DAILY_CAP_CENTS: '50',
    UR_MONTHLY_CAP_CENTS: '500',
    UR_USER_DAILY_CAP_CENTS: '20',
    UR_USER_MONTHLY_CAP_CENTS: '200',
  }
}

describe('un-reminder-worker', () => {
  beforeAll(async () => {
    saKeyJson = (await generateTestServiceAccount()).keyJson
  })

  beforeEach(async () => {
    fetchCallIndex = 0
    fetchResponses.length = 0
    globalThis.fetch = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => {
      const queued = fetchResponses[fetchCallIndex++]
      if (!queued) {
        return new Response('No mock response queued', { status: 500 })
      }
      return new Response(queued.body, {
        status: queued.status,
        headers: queued.headers ?? { 'Content-Type': 'application/json' },
      })
    }) as typeof fetch

    // Clean KV state between tests (best-effort: workerd WebSocket may have restarted, leaving KV already empty)
    try {
      const e = testEnv()
      const keys = await e.UR_SPEND.list()
      for (const key of keys.keys) {
        await e.UR_SPEND.delete(key.name)
      }
    } catch {
      // Miniflare KV is in-memory; a workerd restart clears it automatically
    }

    // Outside the silent catch above: a seed failure must surface as itself, not as 401s
    await seedToken(TOKEN_A, 'alex')
    await seedToken(TOKEN_B, 'friend')
    await seedToken(TOKEN_C, 'play-user', true, false)
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
  })

  // ---- Auth tests ----

  it('returns 401 on missing Authorization', async () => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(401)
  })

  it('returns 401 on the wrong secret for a known id', async () => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer('ur1_000000000000000a_' + 'c'.repeat(64)),
      },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(401)
  })

  it.each([
    ['a malformed token', { Authorization: 'Bearer not-a-token' }],
    ['a non-Bearer scheme', { Authorization: `Basic ${TOKEN_A}` }],
    ['a well-formed token nobody minted', bearer('ur1_00000000000000ff_' + 'f'.repeat(64))],
  ])('returns 401 on %s', async (_name, headers) => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...headers },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(401)
    expect(fetchCallIndex).toBe(0)
  })

  it('accepts two users\' tokens and tells them apart', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    try {
      for (const token of [TOKEN_A, TOKEN_B]) {
        mockRequestySuccess({ descriptionLadder: Array(6).fill('A description.') })
        const req = makeRequest('/v1/habit-fields', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...bearer(token) },
          body: { title: 'Meditate' },
        })
        const ctx = createExecutionContext()
        const res = await app.fetch(req, testEnv(), ctx)
        await waitOnExecutionContext(ctx)
        expect(res.status).toBe(200)
      }
      expect(log).toHaveBeenCalledWith('[auth] authenticated', { id: '000000000000000a', label: 'alex' })
      expect(log).toHaveBeenCalledWith('[auth] authenticated', { id: '000000000000000b', label: 'friend' })
    } finally {
      log.mockRestore()
    }
  })

  it('rejects a disabled token while the others keep working', async () => {
    await seedToken(TOKEN_B, 'friend', false)
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      mockRequestySuccess({ descriptionLadder: Array(6).fill('A description.') })
      for (const [token, status] of [[TOKEN_B, 401], [TOKEN_A, 200]] as const) {
        const req = makeRequest('/v1/habit-fields', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...bearer(token) },
          body: { title: 'Meditate' },
        })
        const ctx = createExecutionContext()
        const res = await app.fetch(req, testEnv(), ctx)
        await waitOnExecutionContext(ctx)
        expect(res.status).toBe(status)
      }
    } finally {
      warn.mockRestore()
    }
  })

  it('returns 503 without calling upstream when the token store is unreachable', async () => {
    const get = vi.spyOn(env.UR_TOKENS, 'get').mockRejectedValue(new Error('kv down'))
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      const req = makeRequest('/v1/generate/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
        body: validBody(),
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, testEnv(), ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(503)
      expect(fetchCallIndex).toBe(0)
    } finally {
      get.mockRestore()
      error.mockRestore()
    }
  })

  // ---- Play Integrity tests ----

  it('lets an exempt token through without an integrity header or a Google call', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    try {
      mockRequestySuccess([{ text: 'Stretch!', shape: 'TERSE' }])
      const req = makeRequest('/v1/generate/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
        body: validBody(1),
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, testEnv(), ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(200)
      expect(fetchCallIndex).toBe(1)
      expect(log).toHaveBeenCalledWith('[integrity] exempt token', { id: '000000000000000a', label: 'alex' })
    } finally {
      log.mockRestore()
    }
  })

  it('verifies a Play user\'s token with Google, then generates', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    try {
      const body = validBody(1)
      await mockIntegrityDecode(body)
      mockRequestySuccess([{ text: 'Stretch!', shape: 'TERSE' }])
      const req = makeRequest('/v1/generate/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_C), [integrityWire.header]: 'play-token' },
        body,
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, testEnv(), ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(200)
      expect(fetchCallIndex).toBe(3)

      const fetchMock = globalThis.fetch as unknown as { mock: { calls: Array<[string, RequestInit]> } }
      expect(fetchMock.mock.calls[0][0]).toBe('https://oauth2.googleapis.com/token')
      expect(fetchMock.mock.calls[1][0]).toContain(':decodeIntegrityToken')
      expect(JSON.parse(fetchMock.mock.calls[1][1].body as string)).toEqual({ integrityToken: 'play-token' })
      expect(log).toHaveBeenCalledWith('[integrity] verified', { id: '000000000000000c', label: 'play-user', device: ['MEETS_DEVICE_INTEGRITY'] })
      // The handler still parsed the body the middleware had already read for hashing.
      expect(JSON.parse(fetchMock.mock.calls[2][1].body as string).messages[0].content).toContain('Habit: "Morning stretch"')
    } finally {
      log.mockRestore()
    }
  })

  it('returns 403 missing for a Play user without an integrity header, without calling Google', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      const req = makeRequest('/v1/generate/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_C) },
        body: validBody(1),
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, testEnv(), ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(403)
      expect(await res.json()).toEqual({ error: integrityWire.rejectedError, reason: 'missing' })
      expect(fetchCallIndex).toBe(0)
      expect(warn).toHaveBeenCalledWith('[integrity] rejected', { id: '000000000000000c', label: 'play-user', reason: 'missing' })
    } finally {
      warn.mockRestore()
    }
  })

  it.each<[string, Partial<TokenPayload>, string]>([
    ['an unrecognised build', { appIntegrity: { appRecognitionVerdict: 'UNRECOGNIZED_VERSION' } }, 'unrecognized-app'],
    ['an unlicensed install', { accountDetails: { appLicensingVerdict: 'UNLICENSED' } }, 'unlicensed'],
  ])('returns 403 for %s and never reaches the LLM', async (_name, overrides, reason) => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      const body = validBody(1)
      await mockIntegrityDecode(body, overrides)
      const req = makeRequest('/v1/generate/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_C), [integrityWire.header]: 'play-token' },
        body,
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, testEnv(), ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(403)
      expect(await res.json()).toEqual({ error: integrityWire.rejectedError, reason })
      expect(fetchCallIndex).toBe(2)
    } finally {
      warn.mockRestore()
    }
  })

  it('returns 403 hash-mismatch when the token was bound to a different body', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      await mockIntegrityDecode({ ...validBody(1), habitTitle: 'Something else' })
      const req = makeRequest('/v1/generate/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_C), [integrityWire.header]: 'play-token' },
        body: validBody(1),
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, testEnv(), ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(403)
      expect(await res.json()).toEqual({ error: integrityWire.rejectedError, reason: 'hash-mismatch' })
      expect(fetchCallIndex).toBe(2)
    } finally {
      warn.mockRestore()
    }
  })

  it('generates for a Play user whose device verdict is empty', async () => {
    const body = validBody(1)
    await mockIntegrityDecode(body, { deviceIntegrity: { deviceRecognitionVerdict: [] } })
    mockRequestySuccess([{ text: 'Stretch!', shape: 'TERSE' }])
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_C), [integrityWire.header]: 'play-token' },
      body,
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
  })

  it('returns 403 invalid when Google rejects the token itself', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      enqueueResponse(200, JSON.stringify({ access_token: 'ya29.test' }))
      enqueueResponse(400, JSON.stringify({ error: { message: 'Integrity token cannot be decoded' } }))
      const req = makeRequest('/v1/generate/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_C), [integrityWire.header]: 'garbage' },
        body: validBody(1),
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, testEnv(), ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(403)
      expect(await res.json()).toEqual({ error: integrityWire.rejectedError, reason: 'invalid' })
      expect(fetchCallIndex).toBe(2)
    } finally {
      warn.mockRestore()
    }
  })

  it.each([[429], [503]])('returns 503 after one decode attempt when Google answers %s', async (status) => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      enqueueResponse(200, JSON.stringify({ access_token: 'ya29.test' }))
      enqueueResponse(status, 'quota or outage')
      const req = makeRequest('/v1/generate/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_C), [integrityWire.header]: 'play-token' },
        body: validBody(1),
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, testEnv(), ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(503)
      expect(await res.json()).toEqual({ error: 'Integrity check unavailable' })
      expect(fetchCallIndex).toBe(2)
    } finally {
      error.mockRestore()
    }
  })

  it('returns 503 without any outbound call when the service-account secret is unset', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      const req = makeRequest('/v1/generate/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_C), [integrityWire.header]: 'play-token' },
        body: validBody(1),
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, { ...testEnv(), UR_PLAY_INTEGRITY_SA_KEY: undefined }, ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(503)
      expect(await res.json()).toEqual({ error: 'Service misconfigured' })
      expect(fetchCallIndex).toBe(0)
    } finally {
      error.mockRestore()
    }
  })

  it('gates /v1/habit-fields the same way', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      const req = makeRequest('/v1/habit-fields', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_C) },
        body: { title: 'Meditate' },
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, testEnv(), ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(403)
      expect(await res.json()).toEqual({ error: integrityWire.rejectedError, reason: 'missing' })
      expect(fetchCallIndex).toBe(0)
    } finally {
      warn.mockRestore()
    }
  })

  // ---- Validation tests ----

  it('returns 400 on invalid JSON body', async () => {
    const url = 'http://localhost/v1/generate/batch'
    const req = new Request(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: '{ not valid json',
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(400)
  })

  it('returns 400 on missing habitTitle', async () => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: { habitTags: ['x'], locationName: 'Home', timeOfDay: 'morning', n: 3 },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(400)
  })

  it('returns 400 when n exceeds 50', async () => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: { ...validBody(), n: 51 },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(400)
  })

  it('returns 400 when n is less than 1', async () => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: { ...validBody(), n: 0 },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(400)
  })

  it('returns 400 when n is not an integer', async () => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: { ...validBody(), n: 1.5 },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(400)
  })

  // ---- Spend cap tests ----

  it('returns 402 when daily KV counter over cap', async () => {
    const e = testEnv()
    const dayKey = dailySpendKey()
    await e.UR_SPEND.put(dayKey, '999')

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(402)
    const body = (await res.json()) as { error: string }
    expect(body.error.toLowerCase()).toContain('daily')

    await e.UR_SPEND.delete(dayKey)
  })

  it('returns 402 when monthly KV counter over cap', async () => {
    const e = testEnv()
    const mKey = spendKeys().monthly
    await e.UR_SPEND.put(mKey, '999')

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(402)
    const body = (await res.json()) as { error: string }
    expect(body.error.toLowerCase()).toContain('monthly')

    await e.UR_SPEND.delete(mKey)
  })

  it('returns 402 for the user over their own daily cap while another user still generates', async () => {
    const e = testEnv()
    await e.UR_SPEND.put(dailySpendKey(TOKEN_A), '999')

    const resA = await postBatch(e, TOKEN_A)
    expect(resA.status).toBe(402)
    expect(await resA.json()).toEqual<SpendCapBody>({
      error: spendWire.errors.user.daily,
      capType: spendWire.capTypes.daily,
      capScope: spendWire.capScopes.user,
    })

    mockRequestySuccess([{ text: 'Go stretch!', shape: 'TERSE' }])
    const resB = await postBatch(e, TOKEN_B)
    expect(resB.status).toBe(200)
  })

  it('returns 402 for a user under their own cap when the service-wide daily cap is reached', async () => {
    const e = testEnv()
    await e.UR_SPEND.put(dailySpendKey(), '999')

    const res = await postBatch(e, TOKEN_A)
    expect(res.status).toBe(402)
    expect(await res.json()).toEqual<SpendCapBody>({
      error: spendWire.errors.global.daily,
      capType: spendWire.capTypes.daily,
      capScope: spendWire.capScopes.global,
    })
  })

  it('names the user\'s own cap when both it and the service-wide cap are reached', async () => {
    const e = testEnv()
    await e.UR_SPEND.put(dailySpendKey(TOKEN_A), '999')
    await e.UR_SPEND.put(dailySpendKey(), '999')

    const res = await postBatch(e, TOKEN_A)
    expect(res.status).toBe(402)
    expect(((await res.json()) as SpendCapBody).capScope).toBe(spendWire.capScopes.user)
  })

  it('lets a token with a raised cap override past the per-user default', async () => {
    const e = testEnv()
    await seedToken(TOKEN_A, 'alex', true, true, { dailyCapCents: 40 })
    // Over the 20-cent default, under A's 40-cent override and the 50-cent service cap
    await e.UR_SPEND.put(dailySpendKey(TOKEN_A), '0.30')
    await e.UR_SPEND.put(dailySpendKey(TOKEN_B), '0.30')

    mockRequestySuccess([{ text: 'Go stretch!', shape: 'TERSE' }])
    expect((await postBatch(e, TOKEN_A)).status).toBe(200)

    const resB = await postBatch(e, TOKEN_B)
    expect(resB.status).toBe(402)
    expect(await resB.json()).toMatchObject({ capType: spendWire.capTypes.daily, capScope: spendWire.capScopes.user })
  })

  it('attributes a batch\'s spend to the caller\'s counter and the service-wide one', async () => {
    const e = testEnv()
    mockRequestySuccess([{ text: 'Go stretch!', shape: 'TERSE' }])
    expect((await postBatch(e, TOKEN_A)).status).toBe(200)

    expect(Number(await e.UR_SPEND.get(dailySpendKey(TOKEN_A)))).toBeGreaterThan(0)
    expect(Number(await e.UR_SPEND.get(dailySpendKey()))).toBeGreaterThan(0)
    expect(await e.UR_SPEND.get(dailySpendKey(TOKEN_B))).toBeNull()
  })

  it('returns 503 without calling upstream when a per-user cap env var is malformed', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      const res = await postBatch({ ...testEnv(), UR_USER_DAILY_CAP_CENTS: 'abc' }, TOKEN_A)
      expect(res.status).toBe(503)
      expect(fetchCallIndex).toBe(0)
    } finally {
      error.mockRestore()
    }
  })

  it('lets the request through when the spend counters cannot be read', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      const downKV = {
        get: async () => {
          throw new Error('kv down')
        },
        put: async () => {},
      } as unknown as KVNamespace
      mockRequestySuccess([{ text: 'Go stretch!', shape: 'TERSE' }])
      const res = await postBatch({ ...testEnv(), UR_SPEND: downKV }, TOKEN_A)
      expect(res.status).toBe(200)
      expect(error).toHaveBeenCalledWith('[spendGate] KV read failed, allowing request through:', expect.any(Error))
    } finally {
      error.mockRestore()
    }
  })

  // ---- Success test ----

  it('returns 200 with N variants on success', async () => {
    const variants = [
      { text: 'Stretch time!', shape: 'TERSE' },
      { text: 'Your body needs a break', shape: 'OBSERVATION', actionUrl: 'https://www.youtube.com/results?search_query=stretching' },
      { text: "Let's move!", shape: 'CHALLENGE' },
    ]
    mockRequestySuccess(variants)

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: validBody(3),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { variants: Array<{ text: string; shape: string; actionUrl?: string }>; generationVersion: number }
    expect(body.variants).toEqual(variants.map((v) => ({ ...v, modes: [] })))
    // The toml value flows through ...env; an edit that breaks the >= 1 contract fails here.
    expect(Number.isInteger(body.generationVersion)).toBe(true)
    expect(body.generationVersion).toBeGreaterThanOrEqual(1)
  })

  // ---- generation version tests ----

  it('echoes the configured generation version on a batch', async () => {
    mockRequestySuccess([{ text: 'Stretch time!', shape: 'TERSE' }])

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: validBody(1),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, { ...testEnv(), UR_GENERATION_VERSION: '7' }, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { generationVersion: number }
    expect(body.generationVersion).toBe(7)
  })

  it('returns 503 on a batch without calling upstream when the generation version is malformed', async () => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: validBody(1),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, { ...testEnv(), UR_GENERATION_VERSION: 'abc' }, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(503)
    expect(fetchCallIndex).toBe(0)
  })

  // ---- supportedModes tests ----

  it('scopes generation to the supported modes and echoes each variant\'s modes', async () => {
    const variants = [
      { text: 'Count 20 steps', shape: 'TERSE', modes: ['WALKING'] },
      { text: 'Breathe for 60 seconds', shape: 'TIMEBOXED', modes: [] },
      { text: 'Both ways', shape: 'STATEMENT', modes: ['WALKING', 'TRANSPORT'] },
    ]
    mockRequestySuccess(variants)

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: { ...validBody(), supportedModes: ['WALKING', 'TRANSPORT'] },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { variants: unknown[] }
    expect(body.variants).toEqual(variants)

    const fetchMock = globalThis.fetch as unknown as { mock: { calls: unknown[][] } }
    const requestInit = fetchMock.mock.calls[0][1] as RequestInit
    const upstreamBody = JSON.parse(requestInit.body as string) as { messages: { content: string }[] }
    const prompt = upstreamBody.messages[0].content
    expect(prompt).toContain('- WALKING:')
    expect(prompt).toContain('- TRANSPORT:')
    expect(prompt).not.toContain('- SITTING:')
    expect(prompt).toContain('"modes": array of strings, each one of WALKING, TRANSPORT')
  })

  it('warns with counts when a partial batch drops variants tagged for unsupported modes', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      mockRequestySuccess([
        { text: 'Count 20 steps', shape: 'TERSE', modes: ['WALKING'] },
        { text: 'Sit up straight', shape: 'STATEMENT', modes: ['SITTING'] },
        { text: 'Breathe for 60 seconds', shape: 'TIMEBOXED', modes: [] },
      ])

      const req = makeRequest('/v1/generate/batch', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...bearer(TOKEN_A),
        },
        body: { ...validBody(), supportedModes: ['SITTING'] },
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, testEnv(), ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(200)
      const body = (await res.json()) as { variants: Array<{ text: string }> }
      expect(body.variants.map((v) => v.text)).toEqual(['Sit up straight', 'Breathe for 60 seconds'])

      expect(warn).toHaveBeenCalledWith('[generateBatch] dropped variants tagged for unsupported modes', {
        requested: 3,
        returned: 2,
        dropped: 1,
        modes: ['SITTING'],
      })
    } finally {
      warn.mockRestore()
    }
  })

  it('does not warn when every variant is within the supported modes', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      mockRequestySuccess([
        { text: 'Sit up straight', shape: 'STATEMENT', modes: ['SITTING'] },
        { text: 'Breathe for 60 seconds', shape: 'TIMEBOXED', modes: [] },
      ])

      const req = makeRequest('/v1/generate/batch', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...bearer(TOKEN_A),
        },
        body: { ...validBody(2), supportedModes: ['SITTING'] },
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, testEnv(), ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(200)
      expect(warn.mock.calls.map((call) => call[0])).not.toContain('[generateBatch] dropped variants tagged for unsupported modes')
    } finally {
      warn.mockRestore()
    }
  })

  it('generates across all three modes when the app sends none', async () => {
    mockRequestySuccess([{ text: 'Stretch!', shape: 'TERSE', modes: ['SITTING'] }])

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: validBody(1),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { variants: unknown[] }
    expect(body.variants).toEqual([{ text: 'Stretch!', shape: 'TERSE', modes: ['SITTING'] }])

    const fetchMock = globalThis.fetch as unknown as { mock: { calls: unknown[][] } }
    const requestInit = fetchMock.mock.calls[0][1] as RequestInit
    const upstreamBody = JSON.parse(requestInit.body as string) as { messages: { content: string }[] }
    expect(upstreamBody.messages[0].content).toContain('"modes": array of strings, each one of WALKING, SITTING, TRANSPORT')
  })

  it('reserves the thinking budget above the per-variant content estimate in max_tokens', async () => {
    for (const [n, expectedMaxTokens] of [[3, 3 * 120 + 1024], [50, 6144]] as const) {
      fetchCallIndex = 0
      fetchResponses.length = 0
      mockRequestySuccess([{ text: 'Stretch!', shape: 'TERSE' }])

      const req = makeRequest('/v1/generate/batch', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...bearer(TOKEN_A),
        },
        body: validBody(n),
      })
      const ctx = createExecutionContext()
      const res = await app.fetch(req, testEnv(), ctx)
      await waitOnExecutionContext(ctx)
      expect(res.status).toBe(200)

      const fetchMock = globalThis.fetch as unknown as { mock: { calls: unknown[][] } }
      const requestInit = fetchMock.mock.calls.at(-1)![1] as RequestInit
      const upstreamBody = JSON.parse(requestInit.body as string) as { max_tokens: number }
      expect(upstreamBody.max_tokens).toBe(expectedMaxTokens)
    }
  })

  // ---- personalContext tests ----

  it('injects personalContext into prompt as Style line', async () => {
    const variants = [{ text: 'Stretch!', shape: 'TERSE' }, { text: 'Move!', shape: 'TERSE' }, { text: 'Go!', shape: 'TERSE' }]
    mockRequestySuccess(variants)

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: { ...validBody(), personalContext: 'use words of encouragement' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)

    const fetchMock = globalThis.fetch as unknown as { mock: { calls: unknown[][] } }
    const requestInit = fetchMock.mock.calls[0][1] as RequestInit
    const upstreamBody = JSON.parse(requestInit.body as string) as {
      messages: { content: string }[]
    }
    expect(upstreamBody.messages[0].content).toContain('Style: "use words of encouragement"')
  })

  it('omits Style line when personalContext absent', async () => {
    const variants = [{ text: 'Stretch!', shape: 'TERSE' }, { text: 'Move!', shape: 'TERSE' }, { text: 'Go!', shape: 'TERSE' }]
    mockRequestySuccess(variants)

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)

    const fetchMock = globalThis.fetch as unknown as { mock: { calls: unknown[][] } }
    const requestInit = fetchMock.mock.calls[0][1] as RequestInit
    const upstreamBody = JSON.parse(requestInit.body as string) as {
      messages: { content: string }[]
    }
    expect(upstreamBody.messages[0].content).not.toContain('Style:')
  })

  // ---- Sprite vocabulary tests ----

  it('offers the sprite vocabulary to the model when sprites are supplied', async () => {
    const variants = [{ text: 'Stretch!', shape: 'TERSE' }, { text: 'Move!', shape: 'TERSE' }, { text: 'Go!', shape: 'TERSE' }]
    mockRequestySuccess(variants)

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: { ...validBody(), sprites: [{ tag: 'cape', description: 'mascot in a superhero cape' }] },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)

    const fetchMock = globalThis.fetch as unknown as { mock: { calls: unknown[][] } }
    const requestInit = fetchMock.mock.calls[0][1] as RequestInit
    const upstreamBody = JSON.parse(requestInit.body as string) as {
      messages: { content: string }[]
    }
    const prompt = upstreamBody.messages[0].content
    expect(prompt).toContain('- "spriteTag": string')
    expect(prompt).toContain('Available sprites')
    expect(prompt).toContain('cape')
    expect(prompt).toContain('mascot in a superhero cape')
    expect(prompt).toContain('9. Pair each message with a "spriteTag"')
  })

  it('leaves the prompt sprite-free when sprites are absent', async () => {
    const variants = [{ text: 'Stretch!', shape: 'TERSE' }, { text: 'Move!', shape: 'TERSE' }, { text: 'Go!', shape: 'TERSE' }]
    mockRequestySuccess(variants)

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)

    const fetchMock = globalThis.fetch as unknown as { mock: { calls: unknown[][] } }
    const requestInit = fetchMock.mock.calls[0][1] as RequestInit
    const upstreamBody = JSON.parse(requestInit.body as string) as {
      messages: { content: string }[]
    }
    const prompt = upstreamBody.messages[0].content
    expect(prompt).not.toContain('spriteTag')
    expect(prompt).not.toContain('Available sprites')
  })

  // ---- Retry + 502 test ----

  it('returns 502 after retries on malformed response', async () => {
    mockRequestyMalformed()
    mockRequestyMalformed()

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(502)
  })

  // ---- Empty-string rejection test ----

  it('returns 502 when LLM returns empty strings in variants array', async () => {
    mockRequestySuccess([{ text: '', shape: 'TERSE' }, { text: '', shape: 'TERSE' }, { text: '', shape: 'TERSE' }])
    mockRequestySuccess([{ text: '', shape: 'TERSE' }, { text: '', shape: 'TERSE' }, { text: '', shape: 'TERSE' }])

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: validBody(3),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(502)
  })

  // ---- Shape rejection test ----

  it('returns 502 when the upstream never declares a shape per variant', async () => {
    mockRequestySuccess([{ text: 'Stretch!' }, { text: 'Move!' }, { text: 'Go!' }])
    mockRequestySuccess([{ text: 'Stretch!' }, { text: 'Move!' }, { text: 'Go!' }])

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: validBody(3),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(502)
  })

  // ---- Retry-then-succeed test ----

  it('returns 200 when first call is malformed but retry succeeds', async () => {
    const variants = [{ text: 'Stretch!', shape: 'TERSE' }, { text: 'Move it!', shape: 'TERSE' }, { text: 'Time to go!', shape: 'STATEMENT' }]
    mockRequestyMalformed()
    mockRequestySuccess(variants)

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { variants: Array<{ text: string; shape: string; actionUrl?: string }>; generationVersion: number }
    expect(body.variants).toEqual(variants.map((v) => ({ ...v, modes: [] })))
    // The toml value flows through ...env; an edit that breaks the >= 1 contract fails here.
    expect(Number.isInteger(body.generationVersion)).toBe(true)
    expect(body.generationVersion).toBeGreaterThanOrEqual(1)
  })

  // ---- generation version tests ----

  it('echoes the configured generation version on a batch', async () => {
    mockRequestySuccess([{ text: 'Stretch time!', shape: 'TERSE' }])

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: validBody(1),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, { ...testEnv(), UR_GENERATION_VERSION: '7' }, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { generationVersion: number }
    expect(body.generationVersion).toBe(7)
  })

  it('returns 503 on a batch without calling upstream when the generation version is malformed', async () => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: validBody(1),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, { ...testEnv(), UR_GENERATION_VERSION: 'abc' }, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(503)
    expect(fetchCallIndex).toBe(0)
  })

  // ---- Upstream error test ----

  it('returns 502 when upstream returns non-200', async () => {
    enqueueResponse(500, 'Internal Server Error')
    enqueueResponse(500, 'Internal Server Error')

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(502)
  })

  // ---- Spend counter increment test ----

  it('increments spend counter after successful call', async () => {
    const variants = [{ text: 'Go stretch!', shape: 'TERSE' }]
    mockRequestySuccess(variants)

    const e = testEnv()
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...bearer(TOKEN_A),
      },
      body: validBody(1),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)

    const dayKey = dailySpendKey()
    const dailySpend = await e.UR_SPEND.get(dayKey)
    expect(Number(dailySpend)).toBeGreaterThan(0)
  })

  // ---- Health endpoint test ----

  it('GET /v1/health returns daily spend', async () => {
    const req = makeRequest('/v1/health')
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { status: string; spendUsedToday: number; generationVersion: number }
    expect(body.status).toBe('ok')
    expect(typeof body.spendUsedToday).toBe('number')
    expect(Number.isInteger(body.generationVersion)).toBe(true)
    expect(body.generationVersion).toBeGreaterThanOrEqual(1)
  })

  it('GET /v1/health echoes the configured generation version', async () => {
    const req = makeRequest('/v1/health')
    const ctx = createExecutionContext()
    const res = await app.fetch(req, { ...testEnv(), UR_GENERATION_VERSION: '7' }, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { generationVersion: number }
    expect(body.generationVersion).toBe(7)
  })

  it('GET /v1/health returns 503 when the generation version is malformed', async () => {
    const req = makeRequest('/v1/health')
    const ctx = createExecutionContext()
    const res = await app.fetch(req, { ...testEnv(), UR_GENERATION_VERSION: 'abc' }, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(503)
  })

  // ---- /v1/habit-fields tests ----

  it('returns 401 without Authorization on /v1/habit-fields', async () => {
    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(401)
  })

  it('returns 402 on /v1/habit-fields when daily cap exceeded', async () => {
    const e = testEnv()
    const dayKey = dailySpendKey()
    await e.UR_SPEND.put(dayKey, '999')

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(402)
    const body = (await res.json()) as { error: string }
    expect(body.error.toLowerCase()).toContain('daily')

    await e.UR_SPEND.delete(dayKey)
  })

  it('returns 402 on /v1/habit-fields when monthly cap exceeded', async () => {
    const e = testEnv()
    const mKey = spendKeys().monthly
    await e.UR_SPEND.put(mKey, '999')

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(402)
    const body = (await res.json()) as { error: string }
    expect(body.error.toLowerCase()).toContain('monthly')

    await e.UR_SPEND.delete(mKey)
  })

  it('returns 200 with habit fields on success', async () => {
    const ladder = [
      'Just try sitting for one minute.',
      'Sit quietly for 3 minutes.',
      'A 10-minute daily sit.',
      'A focused 20-minute session.',
      'A structured 30-minute sit each morning.',
      'A dedicated meditation session that defines your relationship with stillness.',
    ]
    mockRequestySuccess({ descriptionLadder: ladder })

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { descriptionLadder: string[] }
    expect(body.descriptionLadder).toEqual(ladder)
  })

  it('returns 502 on /v1/habit-fields when response has fewer than 6 levels', async () => {
    const partial = { descriptionLadder: ['l0', 'l1', 'l2', 'l3', 'l4'] }
    mockRequestySuccess(partial)
    mockRequestySuccess(partial)

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(502)
  })

  it('returns 502 on /v1/habit-fields when response is persistently malformed', async () => {
    mockRequestyMalformed()
    mockRequestyMalformed()

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(502)
  })

  it('returns 502 on /v1/habit-fields when descriptionLadder has fewer than 6 entries', async () => {
    mockRequestySuccess({ descriptionLadder: ['a', 'b', 'c'] })
    mockRequestySuccess({ descriptionLadder: ['a', 'b', 'c'] })

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(502)
  })

  it('returns 502 on /v1/habit-fields when descriptionLadder has more than 6 entries', async () => {
    mockRequestySuccess({ descriptionLadder: Array(7).fill('A description.') })
    mockRequestySuccess({ descriptionLadder: Array(7).fill('A description.') })

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(502)
  })

  it('returns 502 on /v1/habit-fields when descriptionLadder contains non-string entries', async () => {
    mockRequestySuccess({ descriptionLadder: [1, 2, 3, 4, 5, 6] })
    mockRequestySuccess({ descriptionLadder: [1, 2, 3, 4, 5, 6] })

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(502)
  })

  it('returns 400 on /v1/habit-fields with empty title', async () => {
    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: { title: '' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(400)
  })

  it('increments spend counter after successful /v1/habit-fields call', async () => {
    mockRequestySuccess({ descriptionLadder: Array(6).fill('A description.') })

    const e = testEnv()
    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)

    const dayKey = dailySpendKey()
    const dailySpend = await e.UR_SPEND.get(dayKey)
    expect(Number(dailySpend)).toBeGreaterThan(0)
  })

  it('attributes /v1/habit-fields spend to the caller\'s counter and the service-wide one', async () => {
    const e = testEnv()
    mockRequestySuccess({ descriptionLadder: Array(6).fill('A description.') })
    expect((await postHabitFields(e, TOKEN_A)).status).toBe(200)

    expect(Number(await e.UR_SPEND.get(dailySpendKey(TOKEN_A)))).toBeGreaterThan(0)
    expect(Number(await e.UR_SPEND.get(dailySpendKey()))).toBeGreaterThan(0)
    expect(await e.UR_SPEND.get(dailySpendKey(TOKEN_B))).toBeNull()
  })

  it('returns 402 on /v1/habit-fields for the user over their own daily cap', async () => {
    const e = testEnv()
    await e.UR_SPEND.put(dailySpendKey(TOKEN_A), '999')

    const res = await postHabitFields(e, TOKEN_A)
    expect(res.status).toBe(402)
    expect(await res.json()).toMatchObject({ capType: spendWire.capTypes.daily, capScope: spendWire.capScopes.user })
  })

  it('returns 502 on /v1/habit-fields when upstream throws on both attempts', async () => {
    enqueueResponse(500, 'Internal Server Error')
    enqueueResponse(500, 'Internal Server Error')

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(502)
  })

  it('returns 200 on /v1/habit-fields when first HTTP call fails but retry succeeds', async () => {
    enqueueResponse(500, 'Internal Server Error')
    mockRequestySuccess({ descriptionLadder: ['a', 'b', 'c', 'd', 'e', 'f'] })

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...bearer(TOKEN_A) },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { descriptionLadder: string[] }
    expect(body.descriptionLadder).toHaveLength(6)
  })
})
