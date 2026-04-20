import type { MiddlewareHandler } from 'hono'
import type { Env } from '../types'
import { getSpend } from '../lib/spend'

export const spendGate: MiddlewareHandler<{ Bindings: Env }> = async (c, next) => {
  const capDailyCents = parseInt(c.env.UR_DAILY_CAP_CENTS, 10)
  const capMonthlyCents = parseInt(c.env.UR_MONTHLY_CAP_CENTS, 10)

  // Fail closed: if env vars are missing or malformed, block all requests rather
  // than silently allowing unlimited spend.
  if (isNaN(capDailyCents) || isNaN(capMonthlyCents)) {
    console.error('[spendGate] Spend cap env vars missing or invalid — blocking request as fail-safe', {
      UR_DAILY_CAP_CENTS: c.env.UR_DAILY_CAP_CENTS,
      UR_MONTHLY_CAP_CENTS: c.env.UR_MONTHLY_CAP_CENTS,
    })
    return c.json({ error: 'Service misconfigured' }, 503)
  }

  // Convert caps from cents to dollars for comparison with KV values (stored as dollars)
  const capDaily = capDailyCents / 100
  const capMonthly = capMonthlyCents / 100

  let daily = 0, monthly = 0
  try {
    ;({ daily, monthly } = await getSpend(c.env.UR_SPEND))
  } catch (err) {
    // Fail open on KV error — soft cap is a best-effort guardrail per PRD.
    // Log so the issue is detectable, but don't block requests during a KV blip.
    console.error('[spendGate] KV read failed, allowing request through:', err)
    await next()
    return
  }

  if (daily >= capDaily) {
    return c.json({ error: 'Daily spend cap reached', capType: 'daily' }, 402)
  }
  if (monthly >= capMonthly) {
    return c.json({ error: 'Monthly spend cap reached', capType: 'monthly' }, 402)
  }
  await next()
}
