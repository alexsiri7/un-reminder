function utcDay(d: Date): string {
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`
}

function utcMonth(d: Date): string {
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
}

/**
 * KV keys of one scope's counters: the Worker-wide pair (`day:…`, `month:…`) when no token id
 * is given, else that token's pair under `user:<id>:`, so one user's spend lists with
 * `wrangler kv key list --prefix user:<id>:`.
 */
function spendKeys(tokenId?: string): { daily: string; monthly: string } {
  const d = new Date()
  const prefix = tokenId === undefined ? '' : `user:${tokenId}:`
  return { daily: `${prefix}day:${utcDay(d)}`, monthly: `${prefix}month:${utcMonth(d)}` }
}

function secondsUntilMidnightUTC(): number {
  const now = new Date()
  const midnight = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1))
  return Math.max(60, Math.floor((midnight.getTime() - now.getTime()) / 1000))
}

function secondsUntilMonthEnd(): number {
  const now = new Date()
  const nextMonth = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1))
  return Math.max(60, Math.floor((nextMonth.getTime() - now.getTime()) / 1000))
}

/** Read the current daily and monthly spend totals from KV (in USD): one token's, or the Worker's. */
export async function getSpend(kv: KVNamespace, tokenId?: string): Promise<{ daily: number; monthly: number }> {
  const keys = spendKeys(tokenId)
  const [d, m] = await Promise.all([kv.get(keys.daily), kv.get(keys.monthly)])
  return {
    daily: d ? parseFloat(d) : 0,
    monthly: m ? parseFloat(m) : 0,
  }
}

async function putSpend(kv: KVNamespace, tokenId: string | undefined, dollars: number): Promise<void> {
  const keys = spendKeys(tokenId)
  const { daily, monthly } = await getSpend(kv, tokenId)
  await Promise.all([
    kv.put(keys.daily, (daily + dollars).toFixed(6), { expirationTtl: secondsUntilMidnightUTC() }),
    kv.put(keys.monthly, (monthly + dollars).toFixed(6), { expirationTtl: secondsUntilMonthEnd() }),
  ])
}

/**
 * Accumulate spend against the token's daily and monthly KV keys and the Worker-wide ones.
 *
 * NOTE: This is intentionally a soft cap — the read-modify-write is NOT atomic.
 * Concurrent requests can race and slightly under-count spend; with several users the
 * Worker-wide counter is the one most likely to race. Acceptable for a handful of friends.
 * Durable Objects would be needed for exact accounting.
 */
export async function addSpend(kv: KVNamespace, dollars: number, tokenId: string): Promise<void> {
  await Promise.all([putSpend(kv, tokenId, dollars), putSpend(kv, undefined, dollars)])
}
