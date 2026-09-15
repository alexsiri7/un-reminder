import { env } from 'cloudflare:test'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { TOKEN_PATTERN, hashToken, parseTokenId, tokenKey, verifyToken } from './tokens'
import {
  createTokenRecord,
  hashToken as mintSideHashToken,
  mintToken,
  tokenKey as mintSideTokenKey,
} from '../../scripts/tokenRecord.mjs'

const ID = '0123456789abcdef'
const SECRET = 'f'.repeat(64)
const TOKEN = `ur1_${ID}_${SECRET}`

async function seed(token: string, label: string, enabled = true) {
  const id = parseTokenId(token)!
  const record = await createTokenRecord(token, label, new Date('2026-09-15T00:00:00Z'))
  await env.UR_TOKENS.put(tokenKey(id), JSON.stringify({ ...record, enabled }))
  return record
}

describe('TOKEN_PATTERN / parseTokenId', () => {
  it('returns the id of a well-formed token', () => {
    expect(parseTokenId(TOKEN)).toBe(ID)
  })

  it.each([
    ['wrong prefix', `ur2_${ID}_${SECRET}`],
    ['uppercase hex', `ur1_${ID.toUpperCase()}_${SECRET}`],
    ['short id', `ur1_${ID.slice(1)}_${SECRET}`],
    ['short secret', `ur1_${ID}_${SECRET.slice(1)}`],
    ['long secret', `ur1_${ID}_${SECRET}f`],
    ['missing separator', `ur1_${ID}${SECRET}`],
    ['surrounding whitespace', ` ${TOKEN}`],
    ['empty', ''],
  ])('rejects %s', (_name, token) => {
    expect(parseTokenId(token)).toBeNull()
    expect(TOKEN_PATTERN.test(token)).toBe(false)
  })
})

describe('minting side (scripts/tokenRecord.mjs) agrees with the verifying side', () => {
  it('mints tokens the Worker accepts, each one different', () => {
    const a = mintToken()
    const b = mintToken()
    expect(a.token).toMatch(TOKEN_PATTERN)
    expect(parseTokenId(a.token)).toBe(a.id)
    expect(a.token).not.toBe(b.token)
    expect(a.id).not.toBe(b.id)
  })

  it('computes the same salted hash as the Worker', async () => {
    expect(await mintSideHashToken('salt', TOKEN)).toBe(await hashToken('salt', TOKEN))
    expect(mintSideTokenKey(ID)).toBe(tokenKey(ID))
  })

  it('never writes the plaintext token into the record', async () => {
    const record = await createTokenRecord(TOKEN, 'alex', new Date('2026-09-15T00:00:00Z'))
    expect(JSON.stringify(record)).not.toContain(SECRET)
    expect(record).toMatchObject({ label: 'alex', createdAt: '2026-09-15T00:00:00.000Z', enabled: true })
    expect(record.salt).toMatch(/^[0-9a-f]{32}$/)
    expect(record.hash).toBe(await hashToken(record.salt, TOKEN))
  })
})

describe('hashToken', () => {
  it('is deterministic for one salt and differs across salts', async () => {
    expect(await hashToken('a', TOKEN)).toBe(await hashToken('a', TOKEN))
    expect(await hashToken('a', TOKEN)).not.toBe(await hashToken('b', TOKEN))
    expect(await hashToken('a', TOKEN)).toMatch(/^[0-9a-f]{64}$/)
  })
})

describe('verifyToken', () => {
  beforeEach(async () => {
    const keys = await env.UR_TOKENS.list()
    for (const key of keys.keys) await env.UR_TOKENS.delete(key.name)
  })

  it('returns a distinct identity for each seeded token', async () => {
    const other = mintToken().token
    await seed(TOKEN, 'alex')
    await seed(other, 'friend')

    expect(await verifyToken(env.UR_TOKENS, TOKEN)).toEqual({ id: ID, label: 'alex' })
    expect(await verifyToken(env.UR_TOKENS, other)).toEqual({ id: parseTokenId(other), label: 'friend' })
  })

  it('returns null for an unknown id', async () => {
    expect(await verifyToken(env.UR_TOKENS, TOKEN)).toBeNull()
  })

  it('returns null for a known id with the wrong secret', async () => {
    await seed(TOKEN, 'alex')
    expect(await verifyToken(env.UR_TOKENS, `ur1_${ID}_${'e'.repeat(64)}`)).toBeNull()
  })

  it('returns null for a disabled token', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      await seed(TOKEN, 'alex', false)
      expect(await verifyToken(env.UR_TOKENS, TOKEN)).toBeNull()
      expect(warn).toHaveBeenCalledWith('[auth] disabled token', { id: ID, label: 'alex' })
    } finally {
      warn.mockRestore()
    }
  })

  it('returns null for malformed input without touching KV', async () => {
    const get = vi.spyOn(env.UR_TOKENS, 'get')
    try {
      expect(await verifyToken(env.UR_TOKENS, 'not-a-token')).toBeNull()
      expect(get).not.toHaveBeenCalled()
    } finally {
      get.mockRestore()
    }
  })

  it('treats a record missing its hash as absent', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      const { hash: _hash, ...broken } = await seed(TOKEN, 'alex')
      await env.UR_TOKENS.put(tokenKey(ID), JSON.stringify(broken))
      expect(await verifyToken(env.UR_TOKENS, TOKEN)).toBeNull()
      expect(error).toHaveBeenCalledWith('[auth] malformed token record', { id: ID })
    } finally {
      error.mockRestore()
    }
  })

  it('propagates a KV failure', async () => {
    const kv: Pick<KVNamespace, 'get'> = {
      get: async () => {
        throw new Error('kv down')
      },
    }
    await expect(verifyToken(kv, TOKEN)).rejects.toThrow('kv down')
  })
})
