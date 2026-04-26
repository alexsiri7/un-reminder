import type { Context } from 'hono'
import type { Env } from '../types'
import { addSpend } from '../lib/spend'
import { callRequestyWithSchemaRetry, COST_PER_OUTPUT_TOKEN, COST_PER_INPUT_TOKEN } from '../lib/requesty'

interface HabitFieldsRequest {
  title: string
}

interface HabitFieldsResult {
  descriptionLadder: string[]
}

const LEVEL_LABELS = [
  'just starting',
  'unblocked',
  'regular',
  'committed',
  'routine',
  'your practice',
]

function buildPrompt(title: string, strict = false): string {
  const outputInstruction = strict
    ? `Output ONLY valid JSON with exactly the key descriptionLadder whose value is a JSON array of exactly 6 strings. No markdown, no commentary, no code blocks.`
    : `Output JSON with exactly the key "descriptionLadder" whose value is an array of exactly 6 strings. No markdown, no commentary.`
  return (
    `You are a habit description generator for a habit-tracker app.\n` +
    `Habit title: "${title}"\n\n` +
    `Write exactly 6 short descriptions for this habit, one per dedication level (index 0–5):\n` +
    `  0 — just starting: a minimal, one-time or exploratory version (1 sentence)\n` +
    `  1 — unblocked: a low-effort, any-day version (1 sentence)\n` +
    `  2 — regular: a consistent baseline version (1 sentence)\n` +
    `  3 — committed: a meaningful, deliberate version (1 sentence)\n` +
    `  4 — routine: a deep, habitual version (1 sentence)\n` +
    `  5 — your practice: a full, immersive version that defines the habit (1-2 sentences)\n\n` +
    outputInstruction
  )
}

function validate(parsed: unknown): HabitFieldsResult | null {
  if (typeof parsed !== 'object' || parsed === null) return null
  const p = parsed as Record<string, unknown>
  if (!Array.isArray(p.descriptionLadder)) return null
  if (p.descriptionLadder.length !== LEVEL_LABELS.length) return null
  if (!p.descriptionLadder.every((s) => typeof s === 'string' && s.trim() !== '')) return null
  return { descriptionLadder: p.descriptionLadder as string[] }
}

export async function habitFieldsHandler(c: Context<{ Bindings: Env }>): Promise<Response> {
  let body: HabitFieldsRequest
  try {
    body = await c.req.json<HabitFieldsRequest>()
  } catch {
    return c.json({ error: 'Invalid JSON body' }, 400)
  }

  if (!body.title || typeof body.title !== 'string') {
    return c.json({ error: 'title must be a non-empty string' }, 400)
  }

  const result = await callRequestyWithSchemaRetry(
    c.env.UR_REQUESTY_KEY,
    c.env.UR_MODEL,
    buildPrompt(body.title),
    buildPrompt(body.title, /* strict */ true),
    validate,
  )

  if (!result) {
    return c.json({ error: 'Upstream unavailable or returned invalid response' }, 502)
  }

  const spendDollars =
    result.outputTokens * COST_PER_OUTPUT_TOKEN + result.inputTokens * COST_PER_INPUT_TOKEN
  c.executionCtx.waitUntil(
    addSpend(c.env.UR_SPEND, spendDollars).catch((err) =>
      console.error('[habitFields] addSpend failed:', err, { spendDollars }),
    ),
  )

  return c.json(result.data)
}
