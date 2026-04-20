import { describe, it, expect, vi } from 'vitest'
import { getSpend, addSpend } from './spend'

function mockKV(store: Map<string, string> = new Map()): KVNamespace {
  return {
    get: vi.fn((key: string) => Promise.resolve(store.get(key) ?? null)),
    put: vi.fn((key: string, value: string) => {
      store.set(key, value)
      return Promise.resolve()
    }),
    delete: vi.fn(),
    list: vi.fn(),
    getWithMetadata: vi.fn(),
  } as unknown as KVNamespace
}

describe('getSpend', () => {
  it('returns zeros when KV is empty', async () => {
    const kv = mockKV()
    expect(await getSpend(kv)).toEqual({ daily: 0, monthly: 0 })
  })

  it('returns parsed values when KV has data', async () => {
    const store = new Map([
      [`spend:daily:${new Date().toISOString().slice(0, 10)}`, '0.123456'],
      [`spend:monthly:${new Date().toISOString().slice(0, 7)}`, '1.234567'],
    ])
    const kv = mockKV(store)
    const result = await getSpend(kv)
    expect(result.daily).toBeCloseTo(0.123456, 5)
    expect(result.monthly).toBeCloseTo(1.234567, 5)
  })
})

describe('addSpend', () => {
  it('accumulates spend correctly across sequential calls', async () => {
    const store = new Map<string, string>()
    const kv = mockKV(store)
    await addSpend(kv, 0.05)
    await addSpend(kv, 0.10)
    const { daily } = await getSpend(kv)
    expect(daily).toBeCloseTo(0.15, 5)
  })

  it('writes both daily and monthly keys', async () => {
    const store = new Map<string, string>()
    const kv = mockKV(store)
    await addSpend(kv, 0.01)
    const dailyKey = `spend:daily:${new Date().toISOString().slice(0, 10)}`
    const monthlyKey = `spend:monthly:${new Date().toISOString().slice(0, 7)}`
    expect(store.has(dailyKey)).toBe(true)
    expect(store.has(monthlyKey)).toBe(true)
  })

  // NOTE: KV lacks atomic CAS — concurrent calls can under-count spend.
  // This is intentional (soft cap per PRD). Test documents the known behavior:
  it('KNOWN LIMITATION: concurrent writes may under-count spend', async () => {
    const kv = mockKV()
    await Promise.all([addSpend(kv, 0.10), addSpend(kv, 0.10)])
    const { daily } = await getSpend(kv)
    // In real CF KV, result is likely 0.10 not 0.20 due to race.
    // In mock (synchronous Map), either value is acceptable.
    expect(daily).toBeGreaterThan(0)
  })
})
