import { describe, it, expect, beforeAll } from 'vitest'
import { timingSafeEqual } from './timing'

// crypto.subtle.timingSafeEqual is a Cloudflare Workers extension (not in Node Web Crypto).
// Polyfill it for unit tests only when running outside the Workers runtime (e.g. plain Node.js).
// When running inside workerd via vitest-pool-workers, the function is already natively available
// and must NOT be replaced — doing so causes infinite recursion because node:crypto internally
// delegates to crypto.subtle.timingSafeEqual in the Workers runtime.
beforeAll(() => {
  if (typeof (crypto.subtle as unknown as Record<string, unknown>).timingSafeEqual !== 'function') {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const nodeCrypto = require('node:crypto') as typeof import('node:crypto')
    Object.defineProperty(globalThis.crypto.subtle, 'timingSafeEqual', {
      value: (a: ArrayBuffer, b: ArrayBuffer) =>
        nodeCrypto.timingSafeEqual(Buffer.from(a), Buffer.from(b)),
      configurable: true,
      writable: true,
    })
  }
})

describe('timingSafeEqual', () => {
  it('returns true for identical strings', () => {
    expect(timingSafeEqual('secret', 'secret')).toBe(true)
  })

  it('returns false when strings differ', () => {
    expect(timingSafeEqual('secret', 'wrong')).toBe(false)
  })

  it('returns false for empty vs non-empty', () => {
    expect(timingSafeEqual('', 'a')).toBe(false)
    expect(timingSafeEqual('a', '')).toBe(false)
  })

  it('returns true for two empty strings', () => {
    expect(timingSafeEqual('', '')).toBe(true)
  })

  it('handles multi-byte unicode correctly', () => {
    expect(timingSafeEqual('🔑', '🔑')).toBe(true)
    expect(timingSafeEqual('🔑', '🗝')).toBe(false)
  })

  it('returns false for prefix match (length differs)', () => {
    expect(timingSafeEqual('sec', 'secret')).toBe(false)
  })

  it('returns false for same-length but different content', () => {
    expect(timingSafeEqual('aaa', 'aab')).toBe(false)
  })
})
