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

export interface SpriteOption {
  tag: string
  description: string
}

export interface GenerateBatchRequest {
  habitTitle: string
  habitTags: string[]
  locationName: string
  timeOfDay: string
  /** Sprite vocabulary to choose from; absent when an older app build calls. */
  sprites?: SpriteOption[]
  /** Number of notification variants to generate (1–50). */
  n: number
  /** Optional user-defined communication style hint (e.g. "use words of encouragement"). */
  personalContext?: string
}

/** Structural form of a notification, so consecutive nudges differ in kind and not only in words. */
export const VARIANT_SHAPES = ['QUESTION', 'STATEMENT', 'CHALLENGE', 'OBSERVATION', 'TERSE', 'TIMEBOXED'] as const
export type VariantShape = (typeof VARIANT_SHAPES)[number]

export interface NotificationVariant {
  text: string
  shape: VariantShape
  actionUrl?: string
  /** Tag of the sprite to pair with this text; absent when no vocabulary was supplied. */
  spriteTag?: string
}

export interface GenerateBatchResponse {
  variants: NotificationVariant[]
}

export interface HealthResponse {
  status: 'ok'
  spendUsedToday: number
  spendUsedMonth: number
  capDaily: number
  capMonthly: number
}
