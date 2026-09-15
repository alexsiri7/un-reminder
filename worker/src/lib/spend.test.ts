import { describe, it, expect, vi } from 'vitest'
import { getSpend, addSpend, spendKeys } from './spend'

const ID = '0123456789abcdef'
const OTHER_ID = 'fedcba9876543210'

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
    // Spelled out rather than taken from spendKeys: this is the one place the `day:`/`month:`
    // key format is pinned, and the README's `--prefix user:<id>:` example depends on it.
    const d = new Date()
    const dayKey = `day:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`
    const monthKey = `month:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
    const store = new Map([
      [dayKey, '0.123456'],
      [monthKey, '1.234567'],
    ])
    const kv = mockKV(store)
    const result = await getSpend(kv)
    expect(result.daily).toBeCloseTo(0.123456, 5)
    expect(result.monthly).toBeCloseTo(1.234567, 5)
  })

  it('reads one token\'s counters, not the Worker-wide ones', async () => {
    const user = spendKeys(ID)
    const global = spendKeys()
    const kv = mockKV(new Map([
      [user.daily, '0.1'],
      [user.monthly, '0.2'],
      [global.daily, '5'],
      [global.monthly, '6'],
    ]))
    expect(await getSpend(kv, ID)).toEqual({ daily: 0.1, monthly: 0.2 })
    expect(await getSpend(kv)).toEqual({ daily: 5, monthly: 6 })
  })
})

describe('addSpend', () => {
  it('accumulates spend correctly across sequential calls', async () => {
    const store = new Map<string, string>()
    const kv = mockKV(store)
    await addSpend(kv, 0.05, ID)
    await addSpend(kv, 0.10, ID)
    const { daily } = await getSpend(kv)
    expect(daily).toBeCloseTo(0.15, 5)
  })

  it('writes both daily and monthly keys', async () => {
    const store = new Map<string, string>()
    const kv = mockKV(store)
    await addSpend(kv, 0.01, ID)
    const user = spendKeys(ID)
    const global = spendKeys()
    expect([...store.keys()].sort()).toEqual([user.daily, user.monthly, global.daily, global.monthly].sort())
  })

  it('counts each token apart while the Worker-wide counter sums them', async () => {
    const kv = mockKV()
    await addSpend(kv, 0.05, ID)
    await addSpend(kv, 0.10, OTHER_ID)
    expect((await getSpend(kv, ID)).daily).toBeCloseTo(0.05, 5)
    expect((await getSpend(kv, OTHER_ID)).daily).toBeCloseTo(0.10, 5)
    expect((await getSpend(kv)).daily).toBeCloseTo(0.15, 5)
  })

  // NOTE: KV lacks atomic CAS — concurrent calls can under-count spend.
  // This is intentional (soft cap per PRD). Test documents the known behavior:
  it('KNOWN LIMITATION: concurrent writes may under-count spend', async () => {
    const kv = mockKV()
    await Promise.all([addSpend(kv, 0.10, ID), addSpend(kv, 0.10, ID)])
    const { daily } = await getSpend(kv)
    // In real CF KV, result is likely 0.10 not 0.20 due to race.
    // In mock (synchronous Map), either value is acceptable.
    expect(daily).toBeGreaterThan(0)
  })
})
