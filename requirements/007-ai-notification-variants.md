---
id: "007"
title: "AI-generated notification text (cloud variant pool)"
status: "done"
updated: 2026-05-12
---

## Why

Every notification for a habit should look and read differently so the brain doesn't filter it out. Pre-generating a pool of 50 variants means zero LLM latency on the notification hot path.

## What

Cloudflare Worker (`/v1/generate/batch`) generates batches of notification text variants via Gemini Flash through Requesty.ai. Variants stored in the `Variation` table. Pool refilled when unused count drops below 20. Entire pool cleared and regenerated when habit name or description changes. `action_url` field adds optional Watch button to notifications.
