function dailyKey(): string {
  return `spend:daily:${new Date().toISOString().slice(0, 10)}`
}

function monthlyKey(): string {
  return `spend:monthly:${new Date().toISOString().slice(0, 7)}`
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

/** Read the current daily and monthly spend totals from KV (in USD). */
export async function getSpend(kv: KVNamespace): Promise<{ daily: number; monthly: number }> {
  const [d, m] = await Promise.all([
    kv.get(dailyKey()),
    kv.get(monthlyKey()),
  ])
  return {
    daily: d ? parseFloat(d) : 0,
    monthly: m ? parseFloat(m) : 0,
  }
}

/**
 * Accumulate spend against the daily and monthly KV keys.
 *
 * NOTE: This is intentionally a soft cap — the read-modify-write is NOT atomic.
 * Concurrent requests can race and slightly under-count spend, which is acceptable
 * for a single-device app. Durable Objects would be needed for exact accounting.
 */
export async function addSpend(kv: KVNamespace, dollars: number): Promise<void> {
  const { daily, monthly } = await getSpend(kv)
  await Promise.all([
    kv.put(dailyKey(), (daily + dollars).toFixed(6), {
      expirationTtl: secondsUntilMidnightUTC(),
    }),
    kv.put(monthlyKey(), (monthly + dollars).toFixed(6), {
      expirationTtl: secondsUntilMonthEnd(),
    }),
  ])
}
