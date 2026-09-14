---
id: "014"
title: "Cloud AI settings screen"
status: "done"
updated: 2026-09-14
---

## Why

The worker URL and secret are user-configurable at runtime, allowing the user to point to their own worker instance or rotate the secret without a new build.

## What

Settings screen showing worker URL and shared secret fields with save. "Regenerate all variants" button generates a fresh batch for every active habit and swaps it in only once it has landed; a failed generation leaves the habit's previous variants in place, and the button shows in-flight / done / failed counts while work is outstanding.
