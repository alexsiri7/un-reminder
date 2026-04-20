export interface Env {
  // KV namespace for spend tracking
  SPEND_KV: KVNamespace
  // Rate limit binding
  REQUEST_LIMITER: RateLimit
  // Secrets (set via `wrangler secret put`)
  UR_SECRET: string
  REQUESTY_API_KEY: string
  // Vars (from wrangler.toml [vars])
  SPEND_CAP_DAILY_USD: string
  SPEND_CAP_MONTHLY_USD: string
}

export interface HabitInput {
  id: string
  name: string
  fullDescription: string
  lowFloorDescription: string
}

export interface GenerateBatchRequest {
  habits: HabitInput[]
  count: number          // variants per habit, clamped to 1–10
}

export interface HabitVariants {
  habitId: string
  texts: string[]
}

export interface GenerateBatchResponse {
  variants: HabitVariants[]
  spendDollars: number   // approximate cost of this request
}

export interface HealthResponse {
  status: 'ok'
  spendUsedToday: number
  spendUsedMonth: number
  capDaily: number
  capMonthly: number
}
