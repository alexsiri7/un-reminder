import type { Context } from 'hono'
import type { Env, HealthResponse } from '../types'
import { getSpend } from '../lib/spend'

export async function healthHandler(c: Context<{ Bindings: Env }>): Promise<Response> {
  const capDaily = parseFloat(c.env.SPEND_CAP_DAILY_USD)
  const capMonthly = parseFloat(c.env.SPEND_CAP_MONTHLY_USD)
  if (isNaN(capDaily) || isNaN(capMonthly)) {
    console.error('[healthHandler] Spend cap env vars missing or invalid', {
      SPEND_CAP_DAILY_USD: c.env.SPEND_CAP_DAILY_USD,
      SPEND_CAP_MONTHLY_USD: c.env.SPEND_CAP_MONTHLY_USD,
    })
    return c.json({ error: 'Service misconfigured' }, 503)
  }

  let daily = 0, monthly = 0
  try {
    ;({ daily, monthly } = await getSpend(c.env.SPEND_KV))
  } catch (err) {
    console.error('[healthHandler] KV read failed:', err)
    return c.json({ error: 'KV unavailable' }, 503)
  }

  const body: HealthResponse = {
    status: 'ok',
    spendUsedToday: parseFloat(daily.toFixed(4)),
    spendUsedMonth: parseFloat(monthly.toFixed(4)),
    capDaily,
    capMonthly,
  }
  return c.json(body)
}
