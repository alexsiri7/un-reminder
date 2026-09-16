import type { MiddlewareHandler } from 'hono'
import { SPEND_CAP_ERRORS, type AppEnv, type SpendCapResponse, type SpendCapScope, type SpendCapType } from '../types'
import { getSpend } from '../lib/spend'

export const spendGate: MiddlewareHandler<AppEnv> = async (c, next) => {
  const capEnv = {
    UR_DAILY_CAP_CENTS: c.env.UR_DAILY_CAP_CENTS,
    UR_MONTHLY_CAP_CENTS: c.env.UR_MONTHLY_CAP_CENTS,
    UR_USER_DAILY_CAP_CENTS: c.env.UR_USER_DAILY_CAP_CENTS,
    UR_USER_MONTHLY_CAP_CENTS: c.env.UR_USER_MONTHLY_CAP_CENTS,
  }
  const globalDailyCents = parseInt(capEnv.UR_DAILY_CAP_CENTS, 10)
  const globalMonthlyCents = parseInt(capEnv.UR_MONTHLY_CAP_CENTS, 10)
  const userDailyEnvCents = parseInt(capEnv.UR_USER_DAILY_CAP_CENTS, 10)
  const userMonthlyEnvCents = parseInt(capEnv.UR_USER_MONTHLY_CAP_CENTS, 10)

  // Fail closed: if env vars are missing or malformed, block all requests rather
  // than silently allowing unlimited spend.
  if ([globalDailyCents, globalMonthlyCents, userDailyEnvCents, userMonthlyEnvCents].some(isNaN)) {
    console.error('[spendGate] Spend cap env vars missing or invalid — blocking request as fail-safe', capEnv)
    return c.json({ error: 'Service misconfigured' }, 503)
  }

  const { id, label, dailyCapCents, monthlyCapCents } = c.get('tokenIdentity')

  // Caps are configured in cents; KV counters hold dollars
  const caps = {
    user: { daily: (dailyCapCents ?? userDailyEnvCents) / 100, monthly: (monthlyCapCents ?? userMonthlyEnvCents) / 100 },
    global: { daily: globalDailyCents / 100, monthly: globalMonthlyCents / 100 },
  }

  let spend
  try {
    const [user, global] = await Promise.all([getSpend(c.env.UR_SPEND, id), getSpend(c.env.UR_SPEND)])
    spend = { user, global }
  } catch (err) {
    // Fail open on KV error — soft cap is a best-effort guardrail per PRD.
    // Log so the issue is detectable, but don't block requests during a KV blip.
    // A caller cannot induce this path: the keys read are built from the UTC date and a token
    // id that auth already matched against a stored record, never from request bytes.
    console.error('[spendGate] KV read failed, allowing request through:', err)
    await next()
    return
  }

  const reject = (capScope: SpendCapScope, capType: SpendCapType) => {
    console.warn('[spendGate] cap reached', { id, label, capScope, capType })
    const body: SpendCapResponse = { error: SPEND_CAP_ERRORS[capScope][capType], capType, capScope }
    return c.json(body, 402)
  }

  // A user over both their own cap and the service's hears that it is theirs: retrying once
  // the service recovers would only run them into their own cap again.
  if (spend.user.daily >= caps.user.daily) return reject('user', 'daily')
  if (spend.user.monthly >= caps.user.monthly) return reject('user', 'monthly')
  if (spend.global.daily >= caps.global.daily) return reject('global', 'daily')
  if (spend.global.monthly >= caps.global.monthly) return reject('global', 'monthly')
  await next()
}
