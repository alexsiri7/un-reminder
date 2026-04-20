import type { Context } from 'hono'
import type { Env, HealthResponse } from '../types'
import { getSpend } from '../lib/spend'

export async function healthHandler(c: Context<{ Bindings: Env }>): Promise<Response> {
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
    capDaily: parseInt(c.env.UR_DAILY_CAP_CENTS, 10) / 100,
    capMonthly: parseInt(c.env.UR_MONTHLY_CAP_CENTS, 10) / 100,
  }
  return c.json(body)
}
