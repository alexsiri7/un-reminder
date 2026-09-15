---
id: "013"
title: "Cloudflare Worker (LLM proxy)"
status: "done"
updated: 2026-09-15
---

## Why

Calling an LLM API directly from the app would expose the API key in the APK. A personal Cloudflare Worker acts as a private proxy, handles auth, enforces spend caps, and fans out parallel generation.

## What

Hono-based Cloudflare Worker with three routes: `GET /v1/health` (spend status), `POST /v1/generate/batch` (variant generation), `POST /v1/habit-fields` (description autofill). Auth via per-user bearer tokens stored as salted hashes in KV (req 019). Generation requests also carry a Play Integrity token, bound to a hash of the body and decoded with Google before any LLM call: app recognition and licensing are enforced, device integrity is logged only, and tokens minted as integrity-exempt (debug builds) skip the check. Daily and monthly spend caps tracked in KV. Deployed via Wrangler.
