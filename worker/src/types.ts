export interface Env {
  // KV namespace for spend tracking
  UR_SPEND: KVNamespace
  // Rate limit binding
  REQUEST_LIMITER: RateLimit
  // Secrets (set via `wrangler secret put`)
  UR_SHARED_SECRET: string
  UR_REQUESTY_KEY: string
  SENTRY_DSN?: string
  // Vars (from wrangler.toml [vars])
  UR_DAILY_CAP_CENTS: string
  UR_MONTHLY_CAP_CENTS: string
  UR_MODEL: string
}

export interface GenerateBatchRequest {
  habitTitle: string
  habitTags: string[]
  locationName: string
  timeOfDay: string
  /** Number of notification variants to generate (1–50). */
  n: number
}

export interface GenerateBatchResponse {
  variants: string[]
}

export interface HealthResponse {
  status: 'ok'
  spendUsedToday: number
  spendUsedMonth: number
  capDaily: number
  capMonthly: number
}
