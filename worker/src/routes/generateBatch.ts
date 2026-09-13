import type { Context } from 'hono'
import type { ActivityMode, Env, GenerateBatchRequest, GenerateBatchResponse, NotificationVariant, SpriteOption, VariantShape } from '../types'
import { ACTIVITY_MODES, VARIANT_SHAPES } from '../types'
import { addSpend } from '../lib/spend'
import { callRequestyWithSchemaRetry, COST_PER_OUTPUT_TOKEN, COST_PER_INPUT_TOKEN } from '../lib/requesty'
import * as Sentry from '@sentry/cloudflare'

const shapeGuide =
  `- QUESTION: asks the user something\n` +
  `- STATEMENT: a flat declarative\n` +
  `- CHALLENGE: a small dare\n` +
  `- OBSERVATION: notes something about the present moment\n` +
  `- TERSE: two or three words\n` +
  `- TIMEBOXED: names a small duration ("90 seconds of this")`

const modeGuide: Record<ActivityMode, string> = {
  WALKING: 'on foot and moving, hands mostly free',
  SITTING: 'still, at a desk or at home',
  TRANSPORT: 'a passenger on a bus, train or in a car',
}

export function buildPrompt(habitTitle: string, habitTags: string[], locationName: string, timeOfDay: string, personalContext: string, sprites: SpriteOption[], modes: ActivityMode[], n: number, strict = false): string {
  const schema =
    `- "text": string (max 80 chars, the notification message)\n` +
    `- "shape": string, exactly one of ${VARIANT_SHAPES.join(', ')} (the shape the text was written in)\n` +
    `- "modes": array of strings, each one of ${modes.join(', ')} (the activities the text is written for; [] when it reads naturally in any of them)\n` +
    `- "actionUrl": optional string (YouTube search URL when habit benefits from technique demonstration; omit for simple habits)` +
    (sprites.length > 0 ? `\n- "spriteTag": string (exactly one tag from the sprite list below)` : '')
  const outputInstruction = strict
    ? `Output ONLY a raw JSON array of ${n} objects. Each object must have:\n${schema}\nNo markdown, no commentary, no code blocks.`
    : `Output a JSON array of ${n} objects. Each object must have:\n${schema}\nNo markdown, no commentary.`

  const contextLines: string[] = []
  if (habitTags.length > 0) contextLines.push(`Tags: ${habitTags.join(', ')}`)
  if (locationName) contextLines.push(`Location: "${locationName}"`)
  if (timeOfDay) contextLines.push(`Time of day: "${timeOfDay}"`)
  if (personalContext) contextLines.push(`Style: "${personalContext}"`)
  const contextBlock = contextLines.length > 0 ? contextLines.join('\n') + '\n' : ''

  const spriteBlock = sprites.length > 0
    ? `\nAvailable sprites (mascot illustrations), one per line as tag — description:\n` +
      sprites.map((s) => `${s.tag} — ${s.description}`).join('\n') + '\n'
    : ''
  const spriteRule = sprites.length > 0
    ? `9. Pair each message with a "spriteTag" chosen ONLY from the sprite list above. Pick playfully — the costume does not need to match the habit, and an unexpected pairing is better than a literal one. Vary the sprites across the ${n} messages.\n`
    : ''
  const modeBlock =
    `\nThe user does this habit while:\n` +
    modes.map((m) => `- ${m}: ${modeGuide[m]}`).join('\n') + '\n'

  return (
    `You are a notification writer for a habit-tracker app.\n` +
    `Habit: "${habitTitle}"\n` +
    contextBlock +
    modeBlock +
    spriteBlock +
    `\nWrite ${n} short notification messages (max 80 characters each) that make the user act right now.\n` +
    `Each message is written in one of these shapes:\n${shapeGuide}\n` +
    `Rules:\n` +
    `1. Spread the ${n} messages as evenly as possible across all six shapes, so every shape appears whenever ${n} allows it. Declare each message's shape in "shape".\n` +
    `2. Always include a specific quantity, duration, or named target (e.g. "10 reps", "5 minutes", "C major scale"). If the habit gives no specifics, invent a reasonable concrete goal.\n` +
    `3. Make each message fully self-contained: the user knows exactly what to do and when they are done — no extra decision needed.\n` +
    `4. When location or time of day is relevant, weave it into the message naturally.\n` +
    `5. Never use vague phrases like "do a set", "get started", or "work on your habit".\n` +
    `6. Vary tone and the specific goal across all ${n} messages, not only the shape.\n` +
    `7. Include "actionUrl" only when the habit genuinely benefits from technique demonstration (exercise form, musical scales, guided practice). For simple habits ("drink water", "jumping jacks") omit it entirely. When included, use a YouTube search URL of the form https://www.youtube.com/results?search_query=<encoded+query>.\n` +
    `8. Write at least half of the messages so they read naturally whatever the user is doing, with "modes": []. Write the rest specifically for one of the activities listed above, spread across ${modes.length === 1 ? 'it' : 'all of them'}, and declare that activity in "modes". Never tag a message with an activity not listed above.\n` +
    spriteRule +
    outputInstruction
  )
}

const isVariantShape = (value: unknown): value is VariantShape =>
  typeof value === 'string' && (VARIANT_SHAPES as readonly string[]).includes(value)

const isActivityMode = (value: unknown): value is ActivityMode =>
  typeof value === 'string' && (ACTIVITY_MODES as readonly string[]).includes(value)

/**
 * A variant written for an activity outside [supportedModes] is dropped rather than failing
 * the batch: its text is for a context the habit is never in, so keeping it would only take
 * up pool space the app can never draw from.
 */
export function validateVariants(
  parsed: unknown,
  allowedSpriteTags: Set<string> = new Set(),
  supportedModes: readonly ActivityMode[] = ACTIVITY_MODES,
): NotificationVariant[] | null {
  if (!Array.isArray(parsed)) return null
  if (parsed.length === 0) return null
  const result: NotificationVariant[] = []
  for (const item of parsed) {
    if (typeof item !== 'object' || item === null) return null
    const { text, shape, modes, actionUrl, spriteTag } = item as Record<string, unknown>
    if (typeof text !== 'string' || text.trim() === '') return null
    if (!isVariantShape(shape)) return null
    const variantModes = modes === undefined ? [] : modes
    if (!Array.isArray(variantModes) || !variantModes.every(isActivityMode)) return null
    if (!variantModes.every((m) => supportedModes.includes(m))) continue
    if (actionUrl !== undefined) {
      if (typeof actionUrl !== 'string' || !actionUrl.startsWith('https://')) return null
    }
    // An invented tag drops out rather than failing the batch — the app rotates when it is absent.
    const tag = typeof spriteTag === 'string' && allowedSpriteTags.has(spriteTag) ? spriteTag : undefined
    result.push({ text, shape, modes: variantModes, actionUrl: typeof actionUrl === 'string' ? actionUrl : undefined, spriteTag: tag })
  }
  return result.length > 0 ? result : null
}

export async function generateBatchHandler(c: Context<{ Bindings: Env }>): Promise<Response> {
  let body: GenerateBatchRequest
  try {
    body = await c.req.json<GenerateBatchRequest>()
  } catch {
    return c.json({ error: 'Invalid JSON body' }, 400)
  }

  const { habitTitle, habitTags, locationName, timeOfDay, sprites, n, personalContext, supportedModes } = body
  if (!habitTitle || typeof habitTitle !== 'string') {
    return c.json({ error: 'habitTitle must be a non-empty string' }, 400)
  }
  if (typeof n !== 'number' || !Number.isInteger(n) || n < 1 || n > 50) {
    return c.json({ error: 'n must be an integer between 1 and 50' }, 400)
  }

  const tags = Array.isArray(habitTags) ? habitTags : []
  const spriteOptions: SpriteOption[] = (Array.isArray(sprites) ? sprites : []).filter(
    (s): s is SpriteOption =>
      typeof s?.tag === 'string' && s.tag !== '' && typeof s?.description === 'string',
  )
  // A habit done in any mode, or an older app build that sends none, generates across all three.
  const requestedModes = (Array.isArray(supportedModes) ? supportedModes : []).filter(isActivityMode)
  const modes = ACTIVITY_MODES.filter((m) => requestedModes.length === 0 || requestedModes.includes(m))
  const args = [habitTitle, tags, locationName ?? '', timeOfDay ?? '', personalContext ?? '', spriteOptions, modes, n] as const
  const prompt = buildPrompt(...args)
  const strictPrompt = buildPrompt(...args, true)
  const allowedSpriteTags = new Set(spriteOptions.map((s) => s.tag))

  // A variant with a long text, two modes, an actionUrl and a spriteTag runs to ~250 chars,
  // about 85 tokens; a truncated batch is invalid JSON and costs a retry.
  const maxTokens = Math.min(n * 120, 6144)

  const result = await callRequestyWithSchemaRetry(
    c.env.UR_REQUESTY_KEY,
    c.env.UR_MODEL,
    prompt,
    strictPrompt,
    (parsed) => validateVariants(parsed, allowedSpriteTags, modes),
    maxTokens,
    0.9,
  )

  if (!result) {
    return c.json({ error: 'Upstream unavailable or returned invalid response' }, 502)
  }

  const spendDollars =
    result.outputTokens * COST_PER_OUTPUT_TOKEN + result.inputTokens * COST_PER_INPUT_TOKEN
  c.executionCtx.waitUntil(
    addSpend(c.env.UR_SPEND, spendDollars).catch((err) => {
      console.error('[generateBatch] addSpend failed:', err, { spendDollars })
      Sentry.captureException(err instanceof Error ? err : new Error(String(err)), {
        tags: { component: 'generate-batch', failure: 'add-spend' },
        contexts: { spend: { spendDollars } },
      })
    }),
  )

  const response: GenerateBatchResponse = { variants: result.data }
  return c.json(response)
}
