import * as Sentry from '@sentry/cloudflare'

const REQUESTY_URL = 'https://router.requesty.ai/v1/chat/completions'

const toError = (err: unknown): Error =>
  err instanceof Error ? err : new Error(String(err))

// Pricing per token via Requesty for gemini-3-flash-preview (2026-04).
// These constants MUST match the model configured in UR_MODEL (wrangler.toml).
// If you change the model, update pricing here too.
export const COST_PER_OUTPUT_TOKEN = 0.000075 / 1000
export const COST_PER_INPUT_TOKEN = 0.000075 / 1000

export interface RequestyResult {
  text: string
  outputTokens: number
  inputTokens: number
  finishReason?: string
}

/** Single Requesty chat completion call. Throws on non-200. */
export async function callRequesty(
  apiKey: string,
  model: string,
  prompt: string,
  maxTokens = 2048,
  temperature = 0.7,
): Promise<RequestyResult> {
  const resp = await fetch(REQUESTY_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      model,
      messages: [{ role: 'user', content: prompt }],
      max_tokens: maxTokens,
      temperature,
    }),
  })

  if (!resp.ok) {
    const err = await resp.text()
    throw new Error(`Requesty ${resp.status}: ${err}`)
  }

  const json = (await resp.json()) as {
    choices: { message: { content: string }; finish_reason?: string }[]
    usage?: { completion_tokens?: number; prompt_tokens?: number }
  }
  const text = json.choices?.[0]?.message?.content?.trim() ?? ''
  const finishReason = json.choices?.[0]?.finish_reason
  const outputTokens = json.usage?.completion_tokens ?? 0
  const inputTokens = json.usage?.prompt_tokens ?? 0
  return { text, outputTokens, inputTokens, finishReason }
}

/**
 * Call Requesty expecting JSON matching schema T.
 *
 * Retry behaviour (one retry maximum):
 * - HTTP error (non-200): retries after a 1 s delay using the original `prompt`.
 * - JSON parse / schema validation failure: retries immediately with `stricterPrompt`.
 *
 * Returns null if both attempts fail (caller should 502).
 * Accumulated tokens include both attempts.
 */
export async function callRequestyWithSchemaRetry<T>(
  apiKey: string,
  model: string,
  prompt: string,
  stricterPrompt: string,
  validate: (parsed: unknown) => T | null,
  maxTokens = 2048,
  temperature = 0.7,
): Promise<{ data: T; outputTokens: number; inputTokens: number } | null> {
  let totalOutputTokens = 0
  let totalInputTokens = 0
  let httpErrorOnFirstAttempt = false

  for (const isRetry of [false, true]) {
    // For HTTP error retries, wait 1s to let the upstream recover before retrying.
    if (isRetry && httpErrorOnFirstAttempt) {
      await new Promise((r) => setTimeout(r, 1000))
    }

    // Use stricterPrompt only for JSON/schema retries, not HTTP error retries.
    const promptToUse = isRetry && !httpErrorOnFirstAttempt ? stricterPrompt : prompt

    let result: RequestyResult
    try {
      result = await callRequesty(apiKey, model, promptToUse, maxTokens, temperature)
    } catch (err) {
      const match = err instanceof Error ? err.message.match(/Requesty (\d+)/) : null
      const status = match ? parseInt(match[1], 10) : 0
      console.error('[requesty] callRequesty failed', { isRetry, status, err })
      if (isRetry) {
        // Both attempts failed — report to Sentry and give up.
        Sentry.captureException(toError(err), {
          tags: { 'requesty.failure': 'http' },
          contexts: { requesty: { isRetry, status } },
        })
        return null
      }
      // First attempt failed with HTTP error — mark reason and let retry loop continue.
      httpErrorOnFirstAttempt = true
      continue
    }

    totalOutputTokens += result.outputTokens
    totalInputTokens += result.inputTokens

    const sample = result.text.slice(0, 200)

    if (!result.text) {
      console.warn('[requesty] empty response text', { isRetry, finishReason: result.finishReason })
      if (isRetry) {
        Sentry.captureMessage('Requesty returned empty response text', {
          level: 'warning',
          contexts: { requesty: { isRetry, finishReason: result.finishReason } },
        })
        return null
      }
      continue
    }

    try {
      const parsed = JSON.parse(result.text)
      const validated = validate(parsed)
      if (validated !== null) {
        return { data: validated, outputTokens: totalOutputTokens, inputTokens: totalInputTokens }
      }
      console.warn('[requesty] schema validation failed', { isRetry, text: sample })
      if (isRetry) {
        Sentry.captureMessage('Requesty schema validation failed', {
          level: 'warning',
          contexts: { requesty: { text: sample } },
        })
      }
    } catch (err) {
      console.warn('[requesty] JSON.parse failed', { isRetry, err, text: sample })
      if (isRetry) {
        Sentry.captureException(toError(err), {
          tags: { 'requesty.failure': 'json-parse' },
          contexts: { requesty: { text: sample } },
        })
      }
    }

    if (isRetry) return null
  }
  return null
}
