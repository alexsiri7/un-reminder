import type { MiddlewareHandler } from 'hono'
import type { Env } from '../types'
import { getSpend } from '../lib/spend'

export const spendGate: MiddlewareHandler<{ Bindings: Env }> = async (c, next) => {
  const capDaily = parseFloat(c.env.SPEND_CAP_DAILY_USD)
  const capMonthly = parseFloat(c.env.SPEND_CAP_MONTHLY_USD)
  const { daily, monthly } = await getSpend(c.env.SPEND_KV)

  if (daily >= capDaily) {
    return c.json({ error: 'Daily spend cap reached', capType: 'daily' }, 429)
  }
  if (monthly >= capMonthly) {
    return c.json({ error: 'Monthly spend cap reached', capType: 'monthly' }, 429)
  }
  await next()
}
