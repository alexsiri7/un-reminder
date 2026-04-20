const REQUESTY_URL = 'https://router.requesty.ai/v1/chat/completions'

// Pricing for gemini-3-flash-preview via Requesty (as of 2026-04-20).
// These constants MUST match the model configured in UR_MODEL (wrangler.toml).
// If you change the model, update pricing here too.
export const COST_PER_OUTPUT_TOKEN = 0.000075 / 1000
export const COST_PER_INPUT_TOKEN = 0.000075 / 1000

export interface RequestyResult {
  text: string
  outputTokens: number
  inputTokens: number
}

/** Single Requesty chat completion call. Throws on non-200. */
export async function callRequesty(
  apiKey: string,
  model: string,
  prompt: string,
  maxTokens = 200,
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
    choices: { message: { content: string } }[]
    usage?: { completion_tokens?: number; prompt_tokens?: number }
  }
  const text = json.choices?.[0]?.message?.content?.trim() ?? ''
  const outputTokens = json.usage?.completion_tokens ?? 0
  const inputTokens = json.usage?.prompt_tokens ?? 0
  return { text, outputTokens, inputTokens }
}

/**
 * Call Requesty expecting JSON matching schema T. On malformed response:
 * retries once with stricterPrompt. Returns null if both attempts fail (caller should 502).
 * Accumulated tokens include both attempts.
 */
export async function callRequestyWithSchemaRetry<T>(
  apiKey: string,
  model: string,
  prompt: string,
  stricterPrompt: string,
  validate: (parsed: unknown) => T | null,
  maxTokens = 200,
  temperature = 0.7,
): Promise<{ data: T; outputTokens: number; inputTokens: number } | null> {
  let totalOutputTokens = 0
  let totalInputTokens = 0

  for (const isRetry of [false, true]) {
    let result: RequestyResult
    try {
      result = await callRequesty(apiKey, model, isRetry ? stricterPrompt : prompt, maxTokens, temperature)
    } catch (err) {
      // HTTP errors (non-200) are not retryable — upstream is down or rate-limiting.
      console.error('[requesty] callRequesty failed', { isRetry, err })
      return null
    }

    totalOutputTokens += result.outputTokens
    totalInputTokens += result.inputTokens

    try {
      const parsed = JSON.parse(result.text)
      const validated = validate(parsed)
      if (validated !== null) {
        return { data: validated, outputTokens: totalOutputTokens, inputTokens: totalInputTokens }
      }
      console.warn('[requesty] schema validation failed', { isRetry, text: result.text.slice(0, 200) })
    } catch (err) {
      console.warn('[requesty] JSON.parse failed', { isRetry, err, text: result.text.slice(0, 200) })
    }

    if (isRetry) return null
  }
  return null
}
