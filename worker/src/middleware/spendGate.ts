import type { MiddlewareHandler } from 'hono'
import type { Env } from '../types'
import { getSpend } from '../lib/spend'

export const spendGate: MiddlewareHandler<{ Bindings: Env }> = async (c, next) => {
  const capDaily = parseFloat(c.env.SPEND_CAP_DAILY_USD)
  const capMonthly = parseFloat(c.env.SPEND_CAP_MONTHLY_USD)

  // Fail closed: if env vars are missing or malformed, block all requests rather
  // than silently allowing unlimited spend.
  if (isNaN(capDaily) || isNaN(capMonthly)) {
    console.error('[spendGate] Spend cap env vars missing or invalid — blocking request as fail-safe', {
      SPEND_CAP_DAILY_USD: c.env.SPEND_CAP_DAILY_USD,
      SPEND_CAP_MONTHLY_USD: c.env.SPEND_CAP_MONTHLY_USD,
    })
    return c.json({ error: 'Service misconfigured' }, 503)
  }

  let daily = 0, monthly = 0
  try {
    ;({ daily, monthly } = await getSpend(c.env.SPEND_KV))
  } catch (err) {
    // Fail open on KV error — soft cap is a best-effort guardrail per PRD.
    // Log so the issue is detectable, but don't block requests during a KV blip.
    console.error('[spendGate] KV read failed, allowing request through:', err)
    await next()
    return
  }

  if (daily >= capDaily) {
    return c.json({ error: 'Daily spend cap reached', capType: 'daily' }, 429)
  }
  if (monthly >= capMonthly) {
    return c.json({ error: 'Monthly spend cap reached', capType: 'monthly' }, 429)
  }
  await next()
}
