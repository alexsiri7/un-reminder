---
id: "004"
title: "Automatic dedication level management"
status: "done"
updated: 2026-05-12
---

## Why

Manually managing dedication levels adds friction. Auto-promotion when the user is consistently completing a habit, and auto-demotion on consecutive dismissals, keeps the habit calibrated to the user's actual behavior.

## What

`DedicationLevelManager` auto-promotes `dedication_level` on completion thresholds. 3 consecutive `DISMISSED` triggers demote by 1; at level 0, 3 consecutive dismissals set `active = false` (auto-pause). User can re-activate from the habit editor.
