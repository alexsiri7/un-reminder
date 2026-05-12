---
id: "005"
title: "Time window management"
status: "done"
updated: 2026-05-12
---

## Why

Notifications should only fire during times when the user can actually act on them. Windows give the user control over when prompts are allowed.

## What

`Window` entity with start/end time, days-of-week bitmask, and frequency (1–3 triggers per day). CRUD UI (Windows screen + Window editor). Used by the stochastic trigger to gate when workers fire.
