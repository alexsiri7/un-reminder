import { secondsUntilMidnightUTC, utcDay } from './spend'

/** Today's service-wide count of self-registered tokens, kept in UR_SPEND beside the spend counters. */
export function registrationsKey(): string {
  return `registrations:day:${utcDay(new Date())}`
}

export async function getRegistrationsToday(kv: KVNamespace): Promise<number> {
  const value = await kv.get(registrationsKey())
  return value ? parseInt(value, 10) : 0
}

/**
 * Soft ceiling like the spend counters (src/lib/spend.ts): the read-modify-write is not atomic,
 * so concurrent registrations can each count once over a stale read. Acceptable at this scale;
 * REQUEST_LIMITER still bounds each caller.
 */
export async function countRegistration(kv: KVNamespace): Promise<void> {
  const count = await getRegistrationsToday(kv)
  await kv.put(registrationsKey(), String(count + 1), { expirationTtl: secondsUntilMidnightUTC() })
}
