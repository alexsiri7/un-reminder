import { Hono } from 'hono'

const app = new Hono()

app.get('/v1/health', (c) => {
  return c.json({ status: 'ok', spendUsedToday: 0 })
})

export default app
