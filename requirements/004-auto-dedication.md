---
id: "004"
title: "Automatic dedication level management"
status: "done"
updated: 2026-09-11
---

## Why

Manually managing dedication levels adds friction. Auto-promotion when the user is consistently completing a habit, and auto-demotion on consecutive dismissals, keeps the habit calibrated to the user's actual behavior.

## What

`DismissalTracker` auto-promotes `dedication_level` on completion thresholds. 3 consecutive `DISMISSED` triggers demote by 1, flooring at level 0. A habit is never automatically deactivated; only the user can toggle `active` from the habit editor.
