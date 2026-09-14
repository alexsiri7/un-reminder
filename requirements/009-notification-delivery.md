---
id: "009"
title: "Notification delivery with action buttons"
status: "done"
updated: 2026-09-13
---

## Why

Notifications offer "Open" and "Later" so the prompt reads as an invitation to the thing itself rather than a logging prompt: completion lives in the variant view the notification opens, and a "wrong moment" can still be recorded without reading as "the ask is too big".

## What

Android `NotificationManager` notifications with exactly two action buttons: "Open", which opens the variant view (Reminder Detail screen) and records OPENED, and "Later" (LATER). Tapping the notification body counts as Open. When a variant has an `action_url`, the notification shows a video indicator in its header sub-text instead of a "Watch" button; the video is watched from the variant view. Swiping a trigger notification away is the notification's only dismissal path and is recorded as DISMISSED, which feeds dedication-level demotion. OPENED resolves the trigger, never feeds demotion, holds the habit's normal cooldown, and is the only outcome a later COMPLETED (from the variant view) may replace — no other recorded outcome is ever overwritten. OPENED is only ever written on a trigger-keyed open of the variant view (a notification tap, or a still-FIRED trigger tapped from the Recent list); opened from the menu or widget it records nothing, and completing there inserts a COMPLETED trigger exactly as "did it" does. LATER resolves the trigger without ever feeding dedication-level demotion, and the habit re-enters the pool under its normal cooldown and selection weight. A still-unanswered notification is also cancelled when a later trigger supersedes it, and its trigger is recorded as EXPIRED. The variant text is the notification's title; the habit name, prefixed by one of 20 emoji rotated on the trigger ID for visual distinctiveness, is the line beneath it.
