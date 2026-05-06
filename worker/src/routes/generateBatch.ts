import type { Context } from 'hono'
import type { Env, GenerateBatchRequest, GenerateBatchResponse, NotificationVariant } from '../types'
import { addSpend } from '../lib/spend'
import { callRequestyWithSchemaRetry, COST_PER_OUTPUT_TOKEN, COST_PER_INPUT_TOKEN } from '../lib/requesty'

function buildPrompt(habitTitle: string, habitTags: string[], locationName: string, timeOfDay: string, personalContext: string, n: number, strict = false): string {
  const outputInstruction = strict
    ? `Output ONLY a raw JSON array of ${n} objects. Each object must have:\n` +
      `- "text": string (max 80 chars, the notification message)\n` +
      `- "actionUrl": optional string (YouTube search URL when habit benefits from technique demonstration; omit for simple habits)\n` +
      `No markdown, no commentary, no code blocks.`
    : `Output a JSON array of ${n} objects. Each object must have:\n` +
      `- "text": string (max 80 chars, the notification message)\n` +
      `- "actionUrl": optional string (YouTube search URL when habit benefits from technique demonstration; omit for simple habits)\n` +
      `No markdown, no commentary.`

  const contextLines: string[] = []
  if (habitTags.length > 0) contextLines.push(`Tags: ${habitTags.join(', ')}`)
  if (locationName) contextLines.push(`Location: "${locationName}"`)
  if (timeOfDay) contextLines.push(`Time of day: "${timeOfDay}"`)
  if (personalContext) contextLines.push(`Style: "${personalContext}"`)
  const contextBlock = contextLines.length > 0 ? contextLines.join('\n') + '\n' : ''

  return (
    `You are a notification writer for a habit-tracker app.\n` +
    `Habit: "${habitTitle}"\n` +
    contextBlock +
    `\nWrite ${n} short notification messages (max 80 characters each) that make the user act right now. Rules:\n` +
    `1. Open with an action verb.\n` +
    `2. Always include a specific quantity, duration, or named target (e.g. "10 reps", "5 minutes", "C major scale"). If the habit gives no specifics, invent a reasonable concrete goal.\n` +
    `3. Make each message fully self-contained: the user knows exactly what to do and when they are done — no extra decision needed.\n` +
    `4. When location or time of day is relevant, weave it into the message naturally.\n` +
    `5. Never use vague phrases like "do a set", "get started", or "work on your habit".\n` +
    `6. Vary tone, structure, and the specific goal across all ${n} messages.\n` +
    `7. Include "actionUrl" only when the habit genuinely benefits from technique demonstration (exercise form, musical scales, guided practice). For simple habits ("drink water", "jumping jacks") omit it entirely. When included, use a YouTube search URL of the form https://www.youtube.com/results?search_query=<encoded+query>.\n` +
    outputInstruction
  )
}

export function validateVariants(parsed: unknown): NotificationVariant[] | null {
  if (!Array.isArray(parsed)) return null
  if (parsed.length === 0) return null
  const result: NotificationVariant[] = []
  for (const item of parsed) {
    if (typeof item !== 'object' || item === null) return null
    const { text, actionUrl } = item as Record<string, unknown>
    if (typeof text !== 'string' || text.trim() === '') return null
    if (actionUrl !== undefined && typeof actionUrl !== 'string') return null
    result.push({ text, actionUrl: typeof actionUrl === 'string' ? actionUrl : undefined })
  }
  return result
}

export async function generateBatchHandler(c: Context<{ Bindings: Env }>): Promise<Response> {
  let body: GenerateBatchRequest
  try {
    body = await c.req.json<GenerateBatchRequest>()
  } catch {
    return c.json({ error: 'Invalid JSON body' }, 400)
  }

  const { habitTitle, habitTags, locationName, timeOfDay, n, personalContext } = body
  if (!habitTitle || typeof habitTitle !== 'string') {
    return c.json({ error: 'habitTitle must be a non-empty string' }, 400)
  }
  if (typeof n !== 'number' || !Number.isInteger(n) || n < 1 || n > 50) {
    return c.json({ error: 'n must be an integer between 1 and 50' }, 400)
  }

  const tags = Array.isArray(habitTags) ? habitTags : []
  const args = [habitTitle, tags, locationName ?? '', timeOfDay ?? '', personalContext ?? '', n] as const
  const prompt = buildPrompt(...args)
  const strictPrompt = buildPrompt(...args, true)

  const maxTokens = Math.min(n * 100, 4096)

  const result = await callRequestyWithSchemaRetry(
    c.env.UR_REQUESTY_KEY,
    c.env.UR_MODEL,
    prompt,
    strictPrompt,
    validateVariants,
    maxTokens,
    0.9,
  )

  if (!result) {
    return c.json({ error: 'Upstream unavailable or returned invalid response' }, 502)
  }

  const spendDollars =
    result.outputTokens * COST_PER_OUTPUT_TOKEN + result.inputTokens * COST_PER_INPUT_TOKEN
  c.executionCtx.waitUntil(
    addSpend(c.env.UR_SPEND, spendDollars).catch((err) =>
      console.error('[generateBatch] addSpend failed:', err, { spendDollars }),
    ),
  )

  const response: GenerateBatchResponse = { variants: result.data }
  return c.json(response)
}
