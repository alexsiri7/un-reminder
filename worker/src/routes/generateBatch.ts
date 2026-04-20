import type { Context } from 'hono'
import type { Env, GenerateBatchRequest, GenerateBatchResponse } from '../types'
import { addSpend } from '../lib/spend'
import { callRequestyWithSchemaRetry, COST_PER_OUTPUT_TOKEN, COST_PER_INPUT_TOKEN } from '../lib/requesty'

function buildPrompt(habitTitle: string, habitTags: string[], locationName: string, timeOfDay: string, n: number): string {
  return (
    `You are a notification writer for a habit-tracker app.\n` +
    `Habit: "${habitTitle}"\n` +
    `Tags: ${habitTags.join(', ')}\n` +
    `Location: "${locationName}"\n` +
    `Time of day: "${timeOfDay}"\n\n` +
    `Write ${n} short notification messages (max 80 characters each) that prompt ` +
    `the user to do this habit. Be varied, warm, and specific.\n` +
    `Output a JSON array of ${n} strings. No markdown, no commentary.`
  )
}

function buildStrictPrompt(habitTitle: string, habitTags: string[], locationName: string, timeOfDay: string, n: number): string {
  return (
    `You are a notification writer for a habit-tracker app.\n` +
    `Habit: "${habitTitle}"\n` +
    `Tags: ${habitTags.join(', ')}\n` +
    `Location: "${locationName}"\n` +
    `Time of day: "${timeOfDay}"\n\n` +
    `Write ${n} short notification messages (max 80 characters each) that prompt ` +
    `the user to do this habit. Be varied, warm, and specific.\n` +
    `Output ONLY a raw JSON array of ${n} strings. No markdown, no commentary, no code blocks.`
  )
}

function validateVariants(parsed: unknown): string[] | null {
  if (!Array.isArray(parsed)) return null
  if (!parsed.every((s) => typeof s === 'string')) return null
  return parsed as string[]
}

export async function generateBatchHandler(c: Context<{ Bindings: Env }>): Promise<Response> {
  let body: GenerateBatchRequest
  try {
    body = await c.req.json<GenerateBatchRequest>()
  } catch {
    return c.json({ error: 'Invalid JSON body' }, 400)
  }

  const { habitTitle, habitTags, locationName, timeOfDay, n } = body
  if (!habitTitle || typeof habitTitle !== 'string') {
    return c.json({ error: 'habitTitle must be a non-empty string' }, 400)
  }
  if (typeof n !== 'number' || n < 1 || n > 50) {
    return c.json({ error: 'n must be between 1 and 50' }, 400)
  }

  const prompt = buildPrompt(habitTitle, habitTags ?? [], locationName ?? '', timeOfDay ?? '', n)
  const strictPrompt = buildStrictPrompt(habitTitle, habitTags ?? [], locationName ?? '', timeOfDay ?? '', n)

  const result = await callRequestyWithSchemaRetry(
    c.env.UR_REQUESTY_KEY,
    c.env.UR_MODEL,
    prompt,
    strictPrompt,
    validateVariants,
    400,
  )

  if (!result) {
    return c.json({ error: 'Upstream returned malformed response' }, 502)
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
