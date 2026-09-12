---
id: "009"
title: "Notification delivery with action buttons"
status: "done"
updated: 2026-09-12
---

## Why

Notifications must offer immediate response options — "Did it" and "Later" — so the user can log outcomes without opening the app, and so a "wrong moment" can be recorded without reading as "the ask is too big".

## What

Android `NotificationManager` notifications with "Did it" (COMPLETED) and "Later" (LATER) action buttons. When a variant has an `action_url`, a third "Watch" button opens the URL. Swiping a trigger notification away is the notification's dismissal path and is recorded as DISMISSED; an outcome already recorded is never overwritten. LATER resolves the trigger without ever feeding dedication-level demotion, and the habit re-enters the pool under its normal cooldown and selection weight. A still-unanswered notification is also cancelled when a later trigger supersedes it, and its trigger is recorded as EXPIRED. Notification titles rotate through 20 emoji keyed on trigger ID for visual distinctiveness.
