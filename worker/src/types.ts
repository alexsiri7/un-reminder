import type { TokenIdentity } from './lib/tokens'

export interface Env {
  // KV namespace for spend tracking
  UR_SPEND: KVNamespace
  // KV namespace of per-user auth tokens: `token:<id>` → salted-hash record (src/lib/tokens.ts)
  UR_TOKENS: KVNamespace
  // Rate limit binding
  REQUEST_LIMITER: RateLimit
  // Secrets (set via `wrangler secret put`)
  UR_REQUESTY_KEY: string
  SENTRY_DSN?: string
  // Service-account JSON key that decodes Play Integrity tokens; absent ⇒ non-exempt requests get 503
  UR_PLAY_INTEGRITY_SA_KEY?: string
  // Vars (from wrangler.toml [vars])
  // Service-wide spend ceiling — the backstop on the bill across every token
  UR_DAILY_CAP_CENTS: string
  UR_MONTHLY_CAP_CENTS: string
  // Per-token spend cap a token gets unless its record overrides it (TokenRecord.dailyCapCents / monthlyCapCents)
  UR_USER_DAILY_CAP_CENTS: string
  UR_USER_MONTHLY_CAP_CENTS: string
  UR_MODEL: string
  UR_GENERATION_VERSION: string
  // Service-wide ceiling on tokens minted by POST /v1/register per UTC day
  UR_MAX_REGISTRATIONS_PER_DAY: string
}

/** Hono env of the routes behind authMiddleware, which publishes the caller's identity. */
export type AppEnv = { Bindings: Env; Variables: { tokenIdentity: TokenIdentity } }

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
  /** Activity modes the habit is done in; absent or empty means all of them. */
  supportedModes?: ActivityMode[]
}

/** What the user is physically doing when a notification arrives. */
export const ACTIVITY_MODES = ['WALKING', 'SITTING', 'TRANSPORT'] as const
export type ActivityMode = (typeof ACTIVITY_MODES)[number]

/** Structural form of a notification, so consecutive nudges differ in kind and not only in words. */
export const VARIANT_SHAPES = ['QUESTION', 'STATEMENT', 'CHALLENGE', 'OBSERVATION', 'TERSE', 'TIMEBOXED'] as const
export type VariantShape = (typeof VARIANT_SHAPES)[number]

export interface NotificationVariant {
  text: string
  shape: VariantShape
  /** Modes the text was written for; empty when it reads naturally in any of them. */
  modes: ActivityMode[]
  actionUrl?: string
  /** Tag of the sprite to pair with this text; absent when no vocabulary was supplied. */
  spriteTag?: string
}

export interface GenerateBatchResponse {
  variants: NotificationVariant[]
  /** The version these variants were generated under; the app stamps each stored row with it. */
  generationVersion: number
}

/**
 * Body of a 402 from spendGate. `capScope` says whose budget ran out — the caller's own or the
 * whole service's — and `capType` how long until it refills. Every literal is pinned in
 * test/fixtures/spend-wire.txt, which the app's error handling (#422) asserts against too.
 */
export const SPEND_CAP_TYPES = ['daily', 'monthly'] as const
export type SpendCapType = (typeof SPEND_CAP_TYPES)[number]

export const SPEND_CAP_SCOPES = ['user', 'global'] as const
export type SpendCapScope = (typeof SPEND_CAP_SCOPES)[number]

export interface SpendCapResponse {
  error: string
  capType: SpendCapType
  capScope: SpendCapScope
}

export const SPEND_CAP_ERRORS: Record<SpendCapScope, Record<SpendCapType, string>> = {
  user: { daily: 'Daily spend cap reached', monthly: 'Monthly spend cap reached' },
  global: { daily: 'Service daily spend cap reached', monthly: 'Service monthly spend cap reached' },
}

/** Body of a 200 from POST /v1/register; the only time the plaintext token leaves the Worker. */
export interface RegisterResponse {
  token: string
  id: string
}

/**
 * `error` of the 429 POST /v1/register sends once the day's registrations are used up, distinct
 * from REQUEST_LIMITER's "Rate limit exceeded"; test/fixtures/register-wire.txt pins it for the app.
 */
export const REGISTRATION_CAP_ERROR = 'Daily registration limit reached'

export interface HealthResponse {
  status: 'ok'
  spendUsedToday: number
  spendUsedMonth: number
  capDaily: number
  capMonthly: number
  /** Pools generated under any other version are stale; the app polls this daily. */
  generationVersion: number
  /** Whether UR_PLAY_INTEGRITY_SA_KEY is set; without it every non-exempt request answers 503. */
  integrity: 'configured' | 'unconfigured'
}
