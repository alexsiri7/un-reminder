import {
  env,
  createExecutionContext,
  waitOnExecutionContext,
  fetchMock,
} from 'cloudflare:test'
import { describe, it, expect, beforeAll, afterEach } from 'vitest'
import app from '../src/index'

const SECRET = 'test-secret-value'
const REQUESTY_URL = 'https://router.requesty.ai'

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

function mockRequestySuccess(variants: string[]) {
  fetchMock
    .get(REQUESTY_URL)
    .intercept({ path: '/v1/chat/completions', method: 'POST' })
    .reply(
      200,
      JSON.stringify({
        choices: [{ message: { content: JSON.stringify(variants) } }],
        usage: { prompt_tokens: 100, completion_tokens: 50 },
      }),
      { headers: { 'Content-Type': 'application/json' } },
    )
}

function mockRequestyMalformed() {
  fetchMock
    .get(REQUESTY_URL)
    .intercept({ path: '/v1/chat/completions', method: 'POST' })
    .reply(
      200,
      JSON.stringify({
        choices: [{ message: { content: 'this is not json' } }],
        usage: { prompt_tokens: 100, completion_tokens: 50 },
      }),
      { headers: { 'Content-Type': 'application/json' } },
    )
}

function testEnv() {
  return {
    ...env,
    UR_SHARED_SECRET: SECRET,
    UR_REQUESTY_KEY: 'test-requesty-key',
    UR_MODEL: 'gemini-3-flash-preview',
    UR_DAILY_CAP_CENTS: '50',
    UR_MONTHLY_CAP_CENTS: '500',
  }
}

describe('un-reminder-worker', () => {
  beforeAll(() => {
    fetchMock.activate()
    fetchMock.disableNetConnect()
  })

  afterEach(() => {
    fetchMock.assertNoPendingInterceptors()
  })

  // ---- Auth tests ----

  it('returns 401 on missing secret', async () => {
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

  it('returns 401 on wrong secret', async () => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': 'wrong-secret',
      },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(401)
  })

  // ---- Spend cap tests ----

  it('returns 402 when daily KV counter over cap', async () => {
    const e = testEnv()
    // Pre-set daily spend to exceed cap
    const d = new Date()
    const dayKey = `day:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`
    await e.UR_SPEND.put(dayKey, '999')

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
      },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(402)
    const body = (await res.json()) as { error: string }
    expect(body.error).toContain('daily')

    // Clean up
    await e.UR_SPEND.delete(dayKey)
  })

  it('returns 402 when monthly KV counter over cap', async () => {
    const e = testEnv()
    const d = new Date()
    const mKey = `month:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
    await e.UR_SPEND.put(mKey, '999')

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
      },
      body: validBody(),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(402)
    const body = (await res.json()) as { error: string }
    expect(body.error).toContain('monthly')

    // Clean up
    await e.UR_SPEND.delete(mKey)
  })

  // ---- Success test ----

  it('returns 200 with N variants on success', async () => {
    const variants = ['Stretch time! 🧘', 'Your body needs a break', "Let's move!"]
    mockRequestySuccess(variants)

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
      },
      body: validBody(3),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { variants: string[] }
    expect(body.variants).toEqual(variants)
  })

  // ---- Retry + 502 test ----

  it('returns 502 after retries on malformed response', async () => {
    // Two malformed responses -> 502
    mockRequestyMalformed()
    mockRequestyMalformed()

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
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
    const variants = ['Go stretch!']
    mockRequestySuccess(variants)

    const e = testEnv()
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
      },
      body: validBody(1),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)

    // Verify spend was incremented
    const d = new Date()
    const dayKey = `day:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`
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
    const body = (await res.json()) as { status: string; spendUsedToday: number }
    expect(body.status).toBe('ok')
    expect(typeof body.spendUsedToday).toBe('number')
  })
})
