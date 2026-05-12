---
id: "014"
title: "Cloud AI settings screen"
status: "done"
updated: 2026-05-12
---

## Why

The worker URL and secret are user-configurable at runtime, allowing the user to point to their own worker instance or rotate the secret without a new build.

## What

Settings screen showing worker URL and shared secret fields with save. "Regenerate all variants" button clears the variation pool and re-queues refill for every active habit.
