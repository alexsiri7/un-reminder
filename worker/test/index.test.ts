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

function validHabit() {
  return {
    id: 'habit-1',
    name: 'Morning stretch',
    fullDescription: 'A daily morning stretching routine to wake up the body.',
    lowFloorDescription: 'Just 2 minutes of stretching.',
  }
}

function validBody(count = 1) {
  return { habits: [validHabit()], count }
}

/** Mock a single successful Requesty call returning the given text. */
function mockRequestyReply(text: string) {
  fetchMock
    .get(REQUESTY_URL)
    .intercept({ path: '/v1/chat/completions', method: 'POST' })
    .reply(
      200,
      JSON.stringify({
        choices: [{ message: { content: text } }],
        usage: { prompt_tokens: 100, completion_tokens: 50 },
      }),
      { headers: { 'Content-Type': 'application/json' } },
    )
}

function testEnv() {
  return {
    ...env,
    UR_SECRET: SECRET,
    REQUESTY_API_KEY: 'test-requesty-key',
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

  it('returns 400 on missing habits field', async () => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
      },
      body: { count: 1 },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(400)
  })

  it('returns 400 on empty habits array', async () => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
      },
      body: { habits: [], count: 3 },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(400)
  })

  it('returns 400 when habit is missing required fields', async () => {
    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
      },
      body: { habits: [{ id: 'h1', name: 'Test' }], count: 1 },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(400)
    const json = await res.json() as { error: string }
    expect(json.error).toContain('missing required fields')
  })

  // ---- Spend cap tests ----

  it('returns 429 when daily spend cap is exceeded', async () => {
    const e = testEnv()
    const d = new Date()
    const dayKey = `spend:daily:${d.toISOString().slice(0, 10)}`
    await (e.SPEND_KV as KVNamespace).put(dayKey, '999')

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
    expect(res.status).toBe(429)
    const body = (await res.json()) as { error: string }
    expect(body.error.toLowerCase()).toContain('daily')

    await (e.SPEND_KV as KVNamespace).delete(dayKey)
  })

  it('returns 429 when monthly spend cap is exceeded', async () => {
    const e = testEnv()
    const d = new Date()
    const mKey = `spend:monthly:${d.toISOString().slice(0, 7)}`
    await (e.SPEND_KV as KVNamespace).put(mKey, '999')

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
    expect(res.status).toBe(429)
    const body = (await res.json()) as { error: string }
    expect(body.error.toLowerCase()).toContain('monthly')

    await (e.SPEND_KV as KVNamespace).delete(mKey)
  })

  // ---- Success tests ----

  it('returns 200 with variant text on success', async () => {
    const variantText = 'Stretch time! 🧘'
    mockRequestyReply(variantText)

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
      },
      body: validBody(1),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as {
      variants: Array<{ habitId: string; texts: string[] }>
    }
    expect(body.variants).toHaveLength(1)
    expect(body.variants[0].habitId).toBe('habit-1')
    expect(body.variants[0].texts).toContain(variantText)
  })

  it('returns multiple variants when count > 1', async () => {
    mockRequestyReply('Stretch!')
    mockRequestyReply('Move it!')
    mockRequestyReply('Time to go!')

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
    const body = (await res.json()) as {
      variants: Array<{ habitId: string; texts: string[] }>
    }
    expect(body.variants[0].texts).toHaveLength(3)
  })

  it('returns variants for multiple habits', async () => {
    mockRequestyReply('Stretch!')
    mockRequestyReply('Read now!')

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
      },
      body: {
        habits: [
          validHabit(),
          { id: 'habit-2', name: 'Read', fullDescription: 'Read a book.', lowFloorDescription: 'Read one page.' },
        ],
        count: 1,
      },
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as {
      variants: Array<{ habitId: string; texts: string[] }>
    }
    expect(body.variants).toHaveLength(2)
    expect(body.variants[0].habitId).toBe('habit-1')
    expect(body.variants[1].habitId).toBe('habit-2')
  })

  // ---- Upstream error handling ----

  it('returns 200 with empty texts and error field when upstream returns non-200', async () => {
    fetchMock
      .get(REQUESTY_URL)
      .intercept({ path: '/v1/chat/completions', method: 'POST' })
      .reply(500, 'Internal Server Error')

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
      },
      body: validBody(1),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as {
      variants: Array<{ habitId: string; texts: string[]; error?: string }>
    }
    expect(body.variants[0].texts).toHaveLength(0)
    expect(body.variants[0].error).toBe('all upstream calls failed')
  })

  it('returns 200 with empty texts when upstream returns malformed content', async () => {
    fetchMock
      .get(REQUESTY_URL)
      .intercept({ path: '/v1/chat/completions', method: 'POST' })
      .reply(
        200,
        JSON.stringify({
          choices: [{ message: { content: null } }],
          usage: { prompt_tokens: 100, completion_tokens: 0 },
        }),
        { headers: { 'Content-Type': 'application/json' } },
      )

    const req = makeRequest('/v1/generate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-UR-Secret': SECRET,
      },
      body: validBody(1),
    })
    const ctx = createExecutionContext()
    const res = await app.fetch(req, testEnv(), ctx)
    await waitOnExecutionContext(ctx)
    expect(res.status).toBe(200)
    const body = (await res.json()) as {
      variants: Array<{ habitId: string; texts: string[] }>
    }
    expect(body.variants[0].texts).toHaveLength(0)
  })

  // ---- Spend counter increment test ----

  it('increments spend counter after successful call', async () => {
    mockRequestyReply('Go stretch!')

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
    const dayKey = `spend:daily:${d.toISOString().slice(0, 10)}`
    const dailySpend = await (e.SPEND_KV as KVNamespace).get(dayKey)
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
