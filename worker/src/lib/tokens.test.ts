import { env } from 'cloudflare:test'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { TOKEN_PATTERN, createTokenRecord as workerCreateTokenRecord, hashToken, mintToken as workerMintToken, parseTokenId, tokenKey, verifyToken } from './tokens'
import tokenFormatFixture from '../../test/fixtures/token-format.txt?raw'
import {
  applyCaps,
  createTokenRecord,
  describeToken,
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

/** The app's WorkerTokenTest asserts its regex against the same lines. */
const tokenFormatCases = tokenFormatFixture
  .split('\n')
  .filter((line) => line !== '' && !line.startsWith('#'))
  .map((line) => {
    const [verdict, token] = line.split('\t')
    return { verdict, token, accepted: verdict === 'ok' }
  })

describe('TOKEN_PATTERN / parseTokenId', () => {
  it('returns the id of a well-formed token', () => {
    expect(parseTokenId(TOKEN)).toBe(ID)
  })

  it('shares its fixture with the app', () => {
    expect(tokenFormatCases.filter((c) => c.accepted).length).toBeGreaterThan(0)
    expect(tokenFormatCases.filter((c) => !c.accepted).length).toBeGreaterThan(0)
  })

  it.each(tokenFormatCases)('$verdict', ({ token, accepted }) => {
    expect(TOKEN_PATTERN.test(token)).toBe(accepted)
    expect(parseTokenId(token) !== null).toBe(accepted)
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
    expect(record).not.toHaveProperty('integrityExempt')
  })

  it('writes a cap override only when one is given', async () => {
    const plain = await createTokenRecord(TOKEN, 'alex', new Date('2026-09-15T00:00:00Z'))
    expect(plain).not.toHaveProperty('dailyCapCents')
    expect(plain).not.toHaveProperty('monthlyCapCents')

    const capped = await createTokenRecord(TOKEN, 'alex', new Date('2026-09-15T00:00:00Z'), { dailyCapCents: 100 })
    expect(capped.dailyCapCents).toBe(100)
    expect(capped).not.toHaveProperty('monthlyCapCents')
  })
})

describe('minting side (self-registration) agrees with scripts/tokenRecord.mjs', () => {
  it('mints tokens of the same format, each one different', () => {
    const a = workerMintToken()
    expect(a.token).toMatch(TOKEN_PATTERN)
    expect(parseTokenId(a.token)).toBe(a.id)
    expect(a.token).not.toBe(workerMintToken().token)
  })

  it('writes the same record shape as a default `npm run tokens -- mint`', async () => {
    const now = new Date('2026-09-15T00:00:00Z')
    const worker = await workerCreateTokenRecord(TOKEN, 'self:Pixel', now)
    const script = await createTokenRecord(TOKEN, 'self:Pixel', now)
    expect(Object.keys(worker).sort()).toEqual(Object.keys(script).sort())
    expect({ ...worker, hash: '', salt: '' }).toEqual({ ...script, hash: '', salt: '' })
    expect(worker.salt).toMatch(/^[0-9a-f]{32}$/)
    expect(JSON.stringify(worker)).not.toContain(SECRET)
  })
})

describe('applyCaps (the `caps` subcommand)', () => {
  const capped = () =>
    createTokenRecord(TOKEN, 'alex', new Date('2026-09-15T00:00:00Z'), {
      integrityExempt: true,
      dailyCapCents: 100,
      monthlyCapCents: 900,
    })

  it('replaces the overrides rather than merging: an omitted cap is cleared', async () => {
    const record = applyCaps(await capped(), { dailyCapCents: 50 })
    expect(record.dailyCapCents).toBe(50)
    expect(record).not.toHaveProperty('monthlyCapCents')
  })

  it('clears both overrides when given none', async () => {
    const record = applyCaps(await capped(), {})
    expect(record).not.toHaveProperty('dailyCapCents')
    expect(record).not.toHaveProperty('monthlyCapCents')
  })

  it('leaves everything but the caps untouched', async () => {
    const before = await capped()
    const { dailyCapCents: _daily, monthlyCapCents: _monthly, ...rest } = before
    expect(applyCaps(before, { monthlyCapCents: 300 })).toEqual({ ...rest, monthlyCapCents: 300 })
    expect(before).toMatchObject({ dailyCapCents: 100, monthlyCapCents: 900 })
  })
})

describe('describeToken (the `list` subcommand)', () => {
  const now = new Date('2026-09-15T00:00:00Z')

  it('shows a self-registered token on the defaults, and never its hash or salt', async () => {
    const record = await workerCreateTokenRecord(TOKEN, 'self:Pixel 8', now)
    const line = describeToken(ID, record)
    expect(line).toBe(`${ID}  enabled   2026-09-15T00:00:00.000Z  caps default/default  integrity-gated   "self:Pixel 8"`)
    expect(line).not.toContain(record.hash)
    expect(line).not.toContain(record.salt)
  })

  it('shows a disabled, exempt, capped minted token as such', async () => {
    const record = await createTokenRecord(TOKEN, 'alex-dev', now, { integrityExempt: true, dailyCapCents: 50 })
    expect(describeToken(ID, { ...record, enabled: false })).toBe(
      `${ID}  disabled  2026-09-15T00:00:00.000Z  caps 50/default  integrity-exempt  "alex-dev"`,
    )
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

    expect(await verifyToken(env.UR_TOKENS, TOKEN)).toEqual({ id: ID, label: 'alex', integrityExempt: false })
    expect(await verifyToken(env.UR_TOKENS, other)).toEqual({ id: parseTokenId(other), label: 'friend', integrityExempt: false })
  })

  it('reads the integrity exemption off the record', async () => {
    const record = await createTokenRecord(TOKEN, 'alex-dev', new Date('2026-09-15T00:00:00Z'), { integrityExempt: true })
    expect(record.integrityExempt).toBe(true)
    await env.UR_TOKENS.put(tokenKey(ID), JSON.stringify(record))
    expect(await verifyToken(env.UR_TOKENS, TOKEN)).toEqual({ id: ID, label: 'alex-dev', integrityExempt: true })
  })

  it('reads the cap overrides off the record', async () => {
    const record = await createTokenRecord(TOKEN, 'alex', new Date('2026-09-15T00:00:00Z'), { dailyCapCents: 100, monthlyCapCents: 900 })
    await env.UR_TOKENS.put(tokenKey(ID), JSON.stringify(record))
    expect(await verifyToken(env.UR_TOKENS, TOKEN)).toEqual({
      id: ID,
      label: 'alex',
      integrityExempt: false,
      dailyCapCents: 100,
      monthlyCapCents: 900,
    })
  })

  it.each([
    ['a string', '100'],
    ['zero', 0],
    ['a fraction', 1.5],
    ['negative', -5],
  ])('treats a record whose cap is %s as malformed', async (_name, dailyCapCents) => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      const record = await seed(TOKEN, 'alex')
      await env.UR_TOKENS.put(tokenKey(ID), JSON.stringify({ ...record, dailyCapCents }))
      expect(await verifyToken(env.UR_TOKENS, TOKEN)).toBeNull()
      expect(error).toHaveBeenCalledWith('[auth] malformed token record', { id: ID })
    } finally {
      error.mockRestore()
    }
  })

  it('treats a record whose exemption is not a boolean as malformed', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      const record = await seed(TOKEN, 'alex')
      await env.UR_TOKENS.put(tokenKey(ID), JSON.stringify({ ...record, integrityExempt: 'yes' }))
      expect(await verifyToken(env.UR_TOKENS, TOKEN)).toBeNull()
      expect(error).toHaveBeenCalledWith('[auth] malformed token record', { id: ID })
    } finally {
      error.mockRestore()
    }
  })

  it('accepts a token the Worker minted itself', async () => {
    const { id, token } = workerMintToken()
    await env.UR_TOKENS.put(tokenKey(id), JSON.stringify(await workerCreateTokenRecord(token, 'self:Pixel', new Date())))
    expect(await verifyToken(env.UR_TOKENS, token)).toEqual({ id, label: 'self:Pixel', integrityExempt: false })
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
