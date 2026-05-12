---
id: "008"
title: "Habit description autofill (AI)"
status: "done"
updated: 2026-05-12
---

## Why

Writing six dedication-level descriptions for each habit is tedious. AI autofill reduces the setup friction significantly.

## What

"Autofill descriptions" button in the Habit editor calls the worker's `/v1/habit-fields` endpoint with the habit title and receives 6 description-ladder entries (one per level 0–5). Enabled when habit name is ≥ 2 characters.
