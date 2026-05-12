---
id: "003"
title: "Habit CRUD with dedication levels"
status: "done"
updated: 2026-05-12
---

## Why

Users need to manage their habit list. Dedication levels (0–5) allow gradual commitment — start with a minimal version and increase intensity as the habit takes hold.

## What

`Habit` entity with name, `dedication_level` (0–5), `auto_adjust_level` toggle, `daily_limit`, `cooldown_minutes`, `active` flag, and location associations. CRUD UI (Home screen list + Habit editor). Per-level description ladder stored in `HabitLevelDescriptionEntity`.
