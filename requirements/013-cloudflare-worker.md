---
id: "013"
title: "Cloudflare Worker (LLM proxy)"
status: "done"
updated: 2026-05-12
---

## Why

Calling an LLM API directly from the app would expose the API key in the APK. A personal Cloudflare Worker acts as a private proxy, handles auth, enforces spend caps, and fans out parallel generation.

## What

Hono-based Cloudflare Worker with three routes: `GET /v1/health` (spend status), `POST /v1/generate/batch` (variant generation), `POST /v1/habit-fields` (description autofill). Auth via `X-UR-Secret` header. Daily and monthly spend caps tracked in KV. Deployed via Wrangler.
