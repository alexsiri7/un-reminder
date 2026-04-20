import { Hono } from 'hono'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type Bindings = {
  UR_SPEND: KVNamespace
  UR_SHARED_SECRET: string
  UR_REQUESTY_KEY: string
  UR_MODEL: string
  UR_DAILY_CAP_CENTS: string
  UR_MONTHLY_CAP_CENTS: string
}

interface GenerateRequest {
  habitTitle: string
  habitTags: string[]
  locationName: string
  timeOfDay: string
  n: number
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function todayKey(): string {
  const d = new Date()
  return `day:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`
}

function monthKey(): string {
  const d = new Date()
  return `month:${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
}

async function getSpend(kv: KVNamespace, key: string): Promise<number> {
  const val = await kv.get(key)
  return val ? parseInt(val, 10) : 0
}

function timingSafeEqual(a: string, b: string): Promise<boolean> {
  const enc = new TextEncoder()
  const aBuf = enc.encode(a)
  const bBuf = enc.encode(b)
  if (aBuf.byteLength !== bBuf.byteLength) {
    // Compare against itself to keep constant time, then return false
    crypto.subtle.timingSafeEqual(aBuf, aBuf)
    return Promise.resolve(false)
  }
  return Promise.resolve(crypto.subtle.timingSafeEqual(aBuf, bBuf))
}

function buildSystemPrompt(n: number): string {
  return `You are a notification copywriter for a habit-tracking app called Un-Reminder.
Generate exactly ${n} short, varied notification text variants (each under 100 characters).
Return ONLY a JSON array of strings. No markdown, no code fences, no explanation.
Each variant must be unique — vary tone, phrasing, and structure.
Example: ["Time to stretch! 🧘", "Hey, your body called — it wants a stretch break"]`
}

function buildRetrySystemPrompt(n: number): string {
  return `You MUST return ONLY a valid JSON array of exactly ${n} strings.
No markdown. No code fences. No keys. No explanation. Just a raw JSON array.
Example of valid output: ["variant one", "variant two"]`
}

function buildUserPrompt(req: GenerateRequest): string {
  return `Habit: "${req.habitTitle}"
Tags: ${req.habitTags.join(', ')}
Location: ${req.locationName}
Time of day: ${req.timeOfDay}
Generate ${req.n} notification text variants.`
}

// Pricing: Gemini 3 Flash Preview — $0.50/M input, $3.00/M output
// 1M tokens = 50 cents input, 300 cents output
function estimateCostCents(inputTokens: number, outputTokens: number): number {
  const inputCost = (inputTokens / 1_000_000) * 50   // $0.50 = 50 cents
  const outputCost = (outputTokens / 1_000_000) * 300 // $3.00 = 300 cents
  return Math.ceil(inputCost + outputCost)
}

async function callRequesty(
  apiKey: string,
  model: string,
  systemPrompt: string,
  userPrompt: string,
): Promise<{ variants: string[] | null; inputTokens: number; outputTokens: number }> {
  const res = await fetch('https://router.requesty.ai/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      model,
      temperature: 0.9,
      top_p: 0.95,
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: userPrompt },
      ],
    }),
  })

  if (!res.ok) {
    const errorBody = await res.text().catch(() => '<unreadable>')
    console.error(`Requesty API error: ${res.status} ${res.statusText} — ${errorBody}`)
    return { variants: null, inputTokens: 0, outputTokens: 0 }
  }

  const json = (await res.json()) as {
    choices?: { message?: { content?: string } }[]
    usage?: { prompt_tokens?: number; completion_tokens?: number }
  }

  const content = json.choices?.[0]?.message?.content ?? ''
  const inputTokens = json.usage?.prompt_tokens ?? 0
  const outputTokens = json.usage?.completion_tokens ?? 0

  try {
    const parsed = JSON.parse(content)
    if (Array.isArray(parsed) && parsed.every((v) => typeof v === 'string')) {
      return { variants: parsed, inputTokens, outputTokens }
    }
    console.warn('Requesty response parsed but not a string array:', content)
  } catch {
    console.warn('Requesty response not valid JSON:', content)
  }

  return { variants: null, inputTokens, outputTokens }
}

// ---------------------------------------------------------------------------
// App
// ---------------------------------------------------------------------------

const app = new Hono<{ Bindings: Bindings }>()

// --- Auth middleware ---
app.use('/v1/generate/*', async (c, next) => {
  const secret = c.req.header('X-UR-Secret')
  if (!secret || !(await timingSafeEqual(secret, c.env.UR_SHARED_SECRET))) {
    return c.json({ error: 'unauthorized' }, 401)
  }
  await next()
})

// --- Health ---
app.get('/v1/health', async (c) => {
  const dailySpend = await getSpend(c.env.UR_SPEND, todayKey())
  return c.json({ status: 'ok', spendUsedToday: dailySpend })
})

// --- Batch generate ---
app.post('/v1/generate/batch', async (c) => {
  let body: GenerateRequest
  try {
    body = await c.req.json<GenerateRequest>()
  } catch {
    return c.json({ error: 'invalid JSON in request body' }, 400)
  }

  // Validate
  if (
    !body.habitTitle ||
    !Array.isArray(body.habitTags) ||
    !body.locationName ||
    !body.timeOfDay ||
    typeof body.n !== 'number' ||
    body.n < 1 ||
    body.n > 50
  ) {
    return c.json({ error: 'invalid request body' }, 400)
  }

  // Spend cap check
  const kv = c.env.UR_SPEND
  const dailyCap = parseInt(c.env.UR_DAILY_CAP_CENTS ?? '50', 10)
  const monthlyCap = parseInt(c.env.UR_MONTHLY_CAP_CENTS ?? '500', 10)
  const dKey = todayKey()
  const mKey = monthKey()
  const [dailySpend, monthlySpend] = await Promise.all([getSpend(kv, dKey), getSpend(kv, mKey)])

  if (dailySpend >= dailyCap) {
    return c.json({ error: 'daily spend cap exceeded' }, 402)
  }
  if (monthlySpend >= monthlyCap) {
    return c.json({ error: 'monthly spend cap exceeded' }, 402)
  }

  // Call Requesty
  const model = c.env.UR_MODEL ?? 'gemini-3-flash-preview'
  const userPrompt = buildUserPrompt(body)

  let totalInputTokens = 0
  let totalOutputTokens = 0

  let result = await callRequesty(c.env.UR_REQUESTY_KEY, model, buildSystemPrompt(body.n), userPrompt)
  totalInputTokens += result.inputTokens
  totalOutputTokens += result.outputTokens

  // Retry once with stricter prompt if malformed
  if (!result.variants) {
    result = await callRequesty(c.env.UR_REQUESTY_KEY, model, buildRetrySystemPrompt(body.n), userPrompt)
    totalInputTokens += result.inputTokens
    totalOutputTokens += result.outputTokens
  }

  // Always track spend, even on failure (race accepted per PRD)
  const costCents = estimateCostCents(totalInputTokens, totalOutputTokens)
  if (costCents > 0) {
    await Promise.all([
      kv.put(dKey, String(dailySpend + costCents)),
      kv.put(mKey, String(monthlySpend + costCents)),
    ])
  }

  if (!result.variants) {
    return c.json({ error: 'upstream model returned malformed response' }, 502)
  }

  return c.json({ variants: result.variants })
})

export default app
