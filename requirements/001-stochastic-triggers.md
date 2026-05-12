---
id: "001"
title: "Stochastic window-based triggers (WorkManager)"
status: "done"
updated: 2026-05-12
---

## Why

Fixed-time reminders (7:00 PM every day) suffer from notification blindness — the brain learns to ignore them. Firing at a random time within a window defeats habituation without requiring the user to specify an exact time.

## What

WorkManager one-shot workers that self-re-enqueue after each run using an adaptive delay:
15–30 minutes in normal conditions; 60–90 minutes immediately after a notification fires.
Each worker checks whether the current time is inside an active window before executing the
fire-time pipeline. A watchdog worker detects and restarts dead chains.
