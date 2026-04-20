import { describe, it, expect, vi, afterEach } from 'vitest'
import { callRequesty, callRequestyWithSchemaRetry } from './requesty'

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

function mockFetchResponses(
  ...responses: Array<{ status: number; body: unknown; headers?: Record<string, string> }>
) {
  let i = 0
  globalThis.fetch = vi.fn(async () => {
    const r = responses[i++]
    if (!r) return new Response('No mock response queued', { status: 500 })
    return new Response(JSON.stringify(r.body), {
      status: r.status,
      headers: r.headers ?? { 'Content-Type': 'application/json' },
    })
  }) as typeof fetch
}

describe('callRequesty', () => {
  it('returns parsed text and token counts on success', async () => {
    mockFetchResponses({
      status: 200,
      body: {
        choices: [{ message: { content: 'hello world' } }],
        usage: { prompt_tokens: 10, completion_tokens: 5 },
      },
    })

    const result = await callRequesty('key', 'model', 'prompt')
    expect(result.text).toBe('hello world')
    expect(result.inputTokens).toBe(10)
    expect(result.outputTokens).toBe(5)
  })

  it('throws on non-200 response', async () => {
    mockFetchResponses({ status: 500, body: 'Internal Server Error' })

    await expect(callRequesty('key', 'model', 'prompt')).rejects.toThrow('Requesty 500')
  })

  it('forwards custom temperature to the request body', async () => {
    let sentBody: { temperature: number } | undefined
    globalThis.fetch = vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
      sentBody = JSON.parse(init?.body as string)
      return new Response(
        JSON.stringify({
          choices: [{ message: { content: 'hi' } }],
          usage: { prompt_tokens: 1, completion_tokens: 1 },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      )
    }) as typeof fetch

    await callRequesty('key', 'model', 'prompt', 200, 0.9)
    expect(sentBody?.temperature).toBe(0.9)
  })

  it('defaults tokens to 0 when usage is missing', async () => {
    mockFetchResponses({
      status: 200,
      body: { choices: [{ message: { content: 'hi' } }] },
    })

    const result = await callRequesty('key', 'model', 'prompt')
    expect(result.inputTokens).toBe(0)
    expect(result.outputTokens).toBe(0)
  })
})

describe('callRequestyWithSchemaRetry', () => {
  it('returns validated data on first attempt when valid', async () => {
    mockFetchResponses({
      status: 200,
      body: {
        choices: [{ message: { content: '{"ok":true}' } }],
        usage: { prompt_tokens: 10, completion_tokens: 5 },
      },
    })

    const result = await callRequestyWithSchemaRetry(
      'key', 'model', 'prompt', 'strict',
      (p) => (p as { ok: boolean }).ok ? p : null,
    )
    expect(result).not.toBeNull()
    expect(result!.data).toEqual({ ok: true })
    expect(result!.inputTokens).toBe(10)
    expect(result!.outputTokens).toBe(5)
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
  })

  it('accumulates tokens across retry when first attempt has malformed JSON', async () => {
    mockFetchResponses(
      {
        status: 200,
        body: {
          choices: [{ message: { content: 'not json {' } }],
          usage: { prompt_tokens: 10, completion_tokens: 5 },
        },
      },
      {
        status: 200,
        body: {
          choices: [{ message: { content: '{"ok":true}' } }],
          usage: { prompt_tokens: 12, completion_tokens: 8 },
        },
      },
    )

    const result = await callRequestyWithSchemaRetry(
      'key', 'model', 'prompt', 'strict',
      (p) => (p as { ok: boolean }).ok ? p : null,
    )
    expect(result).not.toBeNull()
    expect(result!.inputTokens).toBe(22) // 10 + 12
    expect(result!.outputTokens).toBe(13) // 5 + 8
  })

  it('uses stricterPrompt on retry attempt', async () => {
    const prompts: string[] = []
    globalThis.fetch = vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
      const body = JSON.parse(init?.body as string)
      prompts.push(body.messages[0].content)
      return new Response(
        JSON.stringify({
          choices: [{ message: { content: 'not valid json' } }],
          usage: { prompt_tokens: 1, completion_tokens: 1 },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      )
    }) as typeof fetch

    await callRequestyWithSchemaRetry('key', 'model', 'normal', 'strict', () => null)
    expect(prompts).toEqual(['normal', 'strict'])
  })

  it('returns null when validate rejects valid JSON on both attempts', async () => {
    mockFetchResponses(
      {
        status: 200,
        body: {
          choices: [{ message: { content: '{"x":1}' } }],
          usage: { prompt_tokens: 1, completion_tokens: 1 },
        },
      },
      {
        status: 200,
        body: {
          choices: [{ message: { content: '{"x":2}' } }],
          usage: { prompt_tokens: 1, completion_tokens: 1 },
        },
      },
    )

    const result = await callRequestyWithSchemaRetry(
      'key', 'model', 'prompt', 'strict',
      () => null, // always rejects
    )
    expect(result).toBeNull()
    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
  })

  it('returns null immediately on HTTP error without retrying', async () => {
    mockFetchResponses({ status: 500, body: 'Internal Server Error' })

    const result = await callRequestyWithSchemaRetry(
      'key', 'model', 'prompt', 'strict',
      (p) => p,
    )
    expect(result).toBeNull()
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
  })

  it('accumulates tokens when first attempt has valid JSON but fails validation then retry succeeds', async () => {
    mockFetchResponses(
      {
        status: 200,
        body: {
          choices: [{ message: { content: '{"wrong":"shape"}' } }],
          usage: { prompt_tokens: 15, completion_tokens: 10 },
        },
      },
      {
        status: 200,
        body: {
          choices: [{ message: { content: '{"right":"shape"}' } }],
          usage: { prompt_tokens: 15, completion_tokens: 10 },
        },
      },
    )

    const result = await callRequestyWithSchemaRetry(
      'key', 'model', 'prompt', 'strict',
      (p) => (p as { right?: string }).right ? p : null,
    )
    expect(result).not.toBeNull()
    expect(result!.data).toEqual({ right: 'shape' })
    expect(result!.inputTokens).toBe(30) // 15 + 15
    expect(result!.outputTokens).toBe(20) // 10 + 10
  })
})
