import type { Context } from 'hono'
import type { Env } from '../types'
import { addSpend } from '../lib/spend'
import { callRequestyWithSchemaRetry, computeSpend } from '../lib/requesty'

interface HabitFieldsRequest {
  title: string
}

interface HabitFieldsResult {
  fullDescription: string
  lowFloorDescription: string
}

function buildPrompt(title: string, strict = false): string {
  const outputInstruction = strict
    ? `Output ONLY valid JSON with exactly the keys fullDescription and lowFloorDescription. No markdown, no commentary, no code blocks.`
    : `Output JSON with exactly the keys "fullDescription" and "lowFloorDescription". No markdown, no commentary.`
  return (
    `You are a habit description generator for a habit-tracker app.\n` +
    `Habit title: "${title}"\n\n` +
    `Write two descriptions for this habit:\n` +
    `1. "fullDescription": A clear, motivating description of the habit (1-2 sentences).\n` +
    `2. "lowFloorDescription": A minimal, easy version of the habit to do on low-motivation days (1 sentence).\n\n` +
    outputInstruction
  )
}

function validate(parsed: unknown): HabitFieldsResult | null {
  if (typeof parsed !== 'object' || parsed === null) return null
  const p = parsed as Record<string, unknown>
  if (typeof p.fullDescription !== 'string' || typeof p.lowFloorDescription !== 'string') return null
  return { fullDescription: p.fullDescription, lowFloorDescription: p.lowFloorDescription }
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
    buildPrompt(body.title, true),
    validate,
  )

  if (!result) {
    return c.json({ error: 'Upstream unavailable or returned invalid response' }, 502)
  }

  const spendDollars = computeSpend(result.outputTokens, result.inputTokens)
  c.executionCtx.waitUntil(
    addSpend(c.env.UR_SPEND, spendDollars).catch((err) =>
      console.error('[habitFields] addSpend failed:', err, { spendDollars }),
    ),
  )

  return c.json(result.data)
}
