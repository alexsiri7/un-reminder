import type { Context } from 'hono'
import type { Env, HealthResponse } from '../types'
import { getSpend } from '../lib/spend'

export async function healthHandler(c: Context<{ Bindings: Env }>): Promise<Response> {
  const capDailyCents = parseInt(c.env.UR_DAILY_CAP_CENTS, 10)
  const capMonthlyCents = parseInt(c.env.UR_MONTHLY_CAP_CENTS, 10)
  if (isNaN(capDailyCents) || isNaN(capMonthlyCents)) {
    console.error('[healthHandler] Spend cap env vars missing or invalid')
    return c.json({ error: 'Service misconfigured' }, 503)
  }

  let daily = 0, monthly = 0
  try {
    ;({ daily, monthly } = await getSpend(c.env.UR_SPEND))
  } catch (err) {
    console.error('[healthHandler] KV read failed:', err)
    return c.json({ error: 'KV unavailable' }, 503)
  }

  const body: HealthResponse = {
    status: 'ok',
    spendUsedToday: parseFloat(daily.toFixed(4)),
    spendUsedMonth: parseFloat(monthly.toFixed(4)),
    capDaily: capDailyCents / 100,
    capMonthly: capMonthlyCents / 100,
  }
  return c.json(body)
}
