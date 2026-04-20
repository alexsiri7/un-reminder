import type { Context } from 'hono'
import type { Env } from '../types'
import { addSpend } from '../lib/spend'
import { callRequestyWithSchemaRetry, COST_PER_OUTPUT_TOKEN, COST_PER_INPUT_TOKEN } from '../lib/requesty'

interface PreviewRequest {
  habit: {
    title: string
    tags: string[]
    notes: string
  }
  locationName: string
}

interface PreviewResult {
  text: string
}

function buildPrompt(title: string, tags: string[], notes: string, locationName: string): string {
  return (
    `You are a notification writer for a habit-tracker app.\n` +
    `Habit: "${title}"\n` +
    `Tags: ${tags.join(', ')}\n` +
    `Notes: "${notes}"\n` +
    `Location: "${locationName}"\n\n` +
    `Write a single short notification message (max 80 characters) that prompts ` +
    `the user to do this habit. Be warm and specific.\n` +
    `Output JSON with exactly the key "text". No markdown, no commentary.`
  )
}

function buildStrictPrompt(title: string, tags: string[], notes: string, locationName: string): string {
  return (
    `You are a notification writer for a habit-tracker app.\n` +
    `Habit: "${title}"\n` +
    `Tags: ${tags.join(', ')}\n` +
    `Notes: "${notes}"\n` +
    `Location: "${locationName}"\n\n` +
    `Write a single short notification message (max 80 characters) that prompts ` +
    `the user to do this habit. Be warm and specific.\n` +
    `Output ONLY valid JSON with exactly the key text. No markdown, no commentary, no code blocks.`
  )
}

function validate(parsed: unknown): PreviewResult | null {
  if (typeof parsed !== 'object' || parsed === null) return null
  const p = parsed as Record<string, unknown>
  if (typeof p.text !== 'string' || p.text.length === 0) return null
  return { text: p.text }
}

export async function previewHandler(c: Context<{ Bindings: Env }>): Promise<Response> {
  let body: PreviewRequest
  try {
    body = await c.req.json<PreviewRequest>()
  } catch {
    return c.json({ error: 'Invalid JSON body' }, 400)
  }

  if (!body.habit?.title || typeof body.habit.title !== 'string') {
    return c.json({ error: 'habit.title must be a non-empty string' }, 400)
  }

  const { title, tags, notes } = body.habit
  const locationName = body.locationName ?? ''

  const result = await callRequestyWithSchemaRetry(
    c.env.UR_REQUESTY_KEY,
    c.env.UR_MODEL,
    buildPrompt(title, tags ?? [], notes ?? '', locationName),
    buildStrictPrompt(title, tags ?? [], notes ?? '', locationName),
    validate,
    100,
  )

  if (!result) {
    return c.json({ error: 'Upstream returned malformed response' }, 502)
  }

  const spendDollars =
    result.outputTokens * COST_PER_OUTPUT_TOKEN + result.inputTokens * COST_PER_INPUT_TOKEN
  c.executionCtx.waitUntil(
    addSpend(c.env.UR_SPEND, spendDollars).catch((err) =>
      console.error('[preview] addSpend failed:', err, { spendDollars }),
    ),
  )

  return c.json({ text: result.data.text })
}
