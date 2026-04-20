import {
  env,
  createExecutionContext,
  waitOnExecutionContext,
} from 'cloudflare:test'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import app from '../src/index'

const SECRET = 'test-secret-value'

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

    // Clean KV state between tests
    const e = testEnv()
    const keys = await e.UR_SPEND.list()
    for (const key of keys.keys) {
      await e.UR_SPEND.delete(key.name)
    }
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
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

  // ---- Validation tests ----

  it('returns 400 on invalid JSON body', async () => {
    const url = 'http://localhost/v1/generate/batch'
    const req = new Request(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
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
        'X-UR-Secret': SECRET,
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
        'X-UR-Secret': SECRET,
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
        'X-UR-Secret': SECRET,
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
        'X-UR-Secret': SECRET,
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
    expect(body.error.toLowerCase()).toContain('daily')

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
    expect(body.error.toLowerCase()).toContain('monthly')

    await e.UR_SPEND.delete(mKey)
  })

  // ---- Success test ----

  it('returns 200 with N variants on success', async () => {
    const variants = ['Stretch time!', 'Your body needs a break', "Let's move!"]
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

  // ---- Retry-then-succeed test ----

  it('returns 200 when first call is malformed but retry succeeds', async () => {
    const variants = ['Stretch!', 'Move it!', 'Time to go!']
    mockRequestyMalformed()
    mockRequestySuccess(variants)

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
    expect(res.status).toBe(200)
    const body = (await res.json()) as { variants: string[] }
    expect(body.variants).toEqual(variants)
  })

  // ---- Upstream error test ----

  it('returns 502 when upstream returns non-200', async () => {
    enqueueResponse(500, 'Internal Server Error')
    enqueueResponse(500, 'Internal Server Error')

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

  // ---- /v1/habit-fields tests ----

  it('returns 401 without secret on /v1/habit-fields', async () => {
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
    const d = new Date()
    const dayKey = `day:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`
    await e.UR_SPEND.put(dayKey, '999')

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
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
    const d = new Date()
    const mKey = `month:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
    await e.UR_SPEND.put(mKey, '999')

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
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
    const fields = { fullDescription: 'Sit quietly for 10 minutes', lowFloorDescription: 'Just sit down' }
    mockRequestySuccess(fields)

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { fullDescription: string; lowFloorDescription: string }
    expect(body.fullDescription).toBe(fields.fullDescription)
    expect(body.lowFloorDescription).toBe(fields.lowFloorDescription)
  })

  it('returns 502 on /v1/habit-fields when response is persistently malformed', async () => {
    mockRequestyMalformed()
    mockRequestyMalformed()

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
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
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
      body: { title: '' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(400)
  })

  // ---- /v1/preview tests ----

  it('returns 401 without secret on /v1/preview', async () => {
    const req = makeRequest('/v1/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: { habit: { title: 'Run', tags: [], notes: '' }, locationName: 'Park' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(401)
  })

  it('returns 402 on /v1/preview when daily cap exceeded', async () => {
    const e = testEnv()
    const d = new Date()
    const dayKey = `day:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`
    await e.UR_SPEND.put(dayKey, '999')

    const req = makeRequest('/v1/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
      body: { habit: { title: 'Run', tags: [], notes: '' }, locationName: 'Park' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(402)
    const body = (await res.json()) as { error: string }
    expect(body.error.toLowerCase()).toContain('daily')

    await e.UR_SPEND.delete(dayKey)
  })

  it('returns 402 on /v1/preview when monthly cap exceeded', async () => {
    const e = testEnv()
    const d = new Date()
    const mKey = `month:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
    await e.UR_SPEND.put(mKey, '999')

    const req = makeRequest('/v1/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
      body: { habit: { title: 'Run', tags: [], notes: '' }, locationName: 'Park' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(402)
    const body = (await res.json()) as { error: string }
    expect(body.error.toLowerCase()).toContain('monthly')

    await e.UR_SPEND.delete(mKey)
  })

  it('returns 200 with preview text on success', async () => {
    const preview = { text: 'Time to run!' }
    mockRequestySuccess(preview)

    const req = makeRequest('/v1/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
      body: { habit: { title: 'Run', tags: ['fitness'], notes: 'Daily jog' }, locationName: 'Park' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as { text: string }
    expect(body.text).toBe('Time to run!')
  })

  it('returns 502 on /v1/preview when response is persistently malformed', async () => {
    mockRequestyMalformed()
    mockRequestyMalformed()

    const req = makeRequest('/v1/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
      body: { habit: { title: 'Run', tags: [], notes: '' }, locationName: 'Park' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(502)
  })

  it('returns 400 on /v1/preview with empty title', async () => {
    const req = makeRequest('/v1/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
      body: { habit: { title: '', tags: [], notes: '' }, locationName: 'Park' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(400)
  })

  it('increments spend counter after successful /v1/habit-fields call', async () => {
    const fields = { fullDescription: 'Sit quietly', lowFloorDescription: 'Just sit' }
    mockRequestySuccess(fields)

    const e = testEnv()
    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)

    const d = new Date()
    const dayKey = `day:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`
    const dailySpend = await e.UR_SPEND.get(dayKey)
    expect(Number(dailySpend)).toBeGreaterThan(0)
  })

  it('increments spend counter after successful /v1/preview call', async () => {
    const preview = { text: 'Time to run!' }
    mockRequestySuccess(preview)

    const e = testEnv()
    const req = makeRequest('/v1/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
      body: { habit: { title: 'Run', tags: ['fitness'], notes: 'Daily jog' }, locationName: 'Park' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, e, ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)

    const d = new Date()
    const dayKey = `day:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`
    const dailySpend = await e.UR_SPEND.get(dayKey)
    expect(Number(dailySpend)).toBeGreaterThan(0)
  })

  it('returns 502 on /v1/habit-fields when upstream throws on both attempts', async () => {
    // callRequesty throws on non-200 — no schema retry on HTTP errors, one 500 suffices
    enqueueResponse(500, 'Internal Server Error')

    const req = makeRequest('/v1/habit-fields', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-UR-Secret': SECRET },
      body: { title: 'Meditate' },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(502)
  })
})
