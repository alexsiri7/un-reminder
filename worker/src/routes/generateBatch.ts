import type { Context } from 'hono'
import type { Env, GenerateBatchRequest, GenerateBatchResponse, HabitVariants } from '../types'
import { addSpend } from '../lib/spend'

const REQUESTY_URL = 'https://router.requesty.ai/v1/chat/completions'
const MODEL = 'google/gemini-2.0-flash'
// Gemini Flash approximate pricing via Requesty (2026-04): ~$0.000075 per 1K output tokens
const COST_PER_OUTPUT_TOKEN = 0.000075 / 1000
// Gemini Flash input token pricing via Requesty (2026-04): ~$0.000075 per 1K input tokens
const COST_PER_INPUT_TOKEN = 0.000075 / 1000

function buildPrompt(habitName: string, fullDesc: string, lowFloorDesc: string): string {
  return (
    `You are a notification writer for a habit-tracker app.\n` +
    `Habit: "${habitName}"\n` +
    `Full description: "${fullDesc}"\n` +
    `Low-floor option: "${lowFloorDesc}"\n\n` +
    `Write a single short notification message (max 80 characters) that prompts ` +
    `the user to do this habit. Be varied, warm, and specific. Output ONLY the ` +
    `notification text — no quotes, no commentary.`
  )
}

async function generateOne(
  apiKey: string,
  prompt: string,
): Promise<{ text: string; outputTokens: number; inputTokens: number }> {
  const resp = await fetch(REQUESTY_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      model: MODEL,
      messages: [{ role: 'user', content: prompt }],
      max_tokens: 80,
      temperature: 0.9,
    }),
  })

  if (!resp.ok) {
    const err = await resp.text()
    throw new Error(`Requesty ${resp.status}: ${err}`)
  }

  const json = await resp.json() as {
    choices: { message: { content: string } }[]
    usage?: { completion_tokens?: number; prompt_tokens?: number }
  }
  const text = json.choices?.[0]?.message?.content?.trim() ?? ''
  const outputTokens = json.usage?.completion_tokens ?? 0
  const inputTokens = json.usage?.prompt_tokens ?? 0
  return { text, outputTokens, inputTokens }
}

export async function generateBatchHandler(c: Context<{ Bindings: Env }>): Promise<Response> {
  let body: GenerateBatchRequest
  try {
    body = await c.req.json<GenerateBatchRequest>()
  } catch {
    return c.json({ error: 'Invalid JSON body' }, 400)
  }

  const { habits, count } = body
  if (!Array.isArray(habits) || habits.length === 0) {
    return c.json({ error: 'habits must be a non-empty array' }, 400)
  }
  const clampedCount = Math.min(Math.max(1, count ?? 1), 10)

  let totalOutputTokens = 0
  let totalInputTokens = 0
  const variantResults: HabitVariants[] = []

  for (const habit of habits) {
    const prompt = buildPrompt(habit.name, habit.fullDescription, habit.lowFloorDescription)
    // Fan out `clampedCount` parallel calls per habit
    const calls = Array.from({ length: clampedCount }, () =>
      generateOne(c.env.REQUESTY_API_KEY, prompt),
    )
    const results = await Promise.allSettled(calls)
    const texts: string[] = []

    for (const r of results) {
      if (r.status === 'fulfilled') {
        totalOutputTokens += r.value.outputTokens
        totalInputTokens += r.value.inputTokens
        if (r.value.text) {
          texts.push(r.value.text)
        } else {
          console.warn(`[generateBatch] Empty text returned for habit ${habit.id}`)
        }
      } else {
        console.error(`[generateBatch] Requesty call failed for habit ${habit.id}:`, r.reason)
      }
    }

    variantResults.push({ habitId: habit.id, texts })
  }

  const spendDollars =
    totalOutputTokens * COST_PER_OUTPUT_TOKEN + totalInputTokens * COST_PER_INPUT_TOKEN
  // Fire-and-forget spend accumulation (don't block response)
  c.executionCtx.waitUntil(
    addSpend(c.env.SPEND_KV, spendDollars).catch((err) =>
      console.error('[generateBatch] addSpend failed, spend not tracked:', err, { spendDollars }),
    ),
  )

  const response: GenerateBatchResponse = {
    variants: variantResults,
    spendDollars: parseFloat(spendDollars.toFixed(6)),
  }
  return c.json(response)
}
