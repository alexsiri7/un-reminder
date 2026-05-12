---
id: "009"
title: "Notification delivery with action buttons"
status: "done"
updated: 2026-05-12
---

## Why

Notifications must offer immediate response options — "Did it" and "Dismiss" — so the user can log outcomes without opening the app.

## What

Android `NotificationManager` notifications with "Did it" (COMPLETED) and "Dismiss" (DISMISSED) action buttons. When a variant has an `action_url`, a third "Watch" button opens the URL. Notification titles rotate through 20 emoji keyed on trigger ID for visual distinctiveness.
