---
created: '2026-09-12'
github_issue: null
id: 018
status: draft
title: 'Context-aware notifications: activity modes, shape variety, Later, and stale-notification
  handling'
updated: '2026-09-12'
---

## Why

Notifications are still being seen and not acted on, and the reasons are no longer about timing randomness or wording — those are already solved. Three distinct causes remain.

First, every notification has the same shape. There are fifty wordings per habit but all of them are the same kind of sentence, so the brain learns the shape and filters it regardless of the words. The widget has the same problem.

Second, the app has no idea what the user is physically doing. A notification that arrives while cycling cannot be acted on at all, and one that arrives on the tube is a genuine opportunity the app currently wastes. Worse, a habit reads completely differently depending on context: a meditation prompt written for sitting at home is the wrong text entirely when the user is walking. Without any notion of activity, the app fires the same ask into contexts where it cannot land.

Third, "not now" has nowhere to go. Dismiss conflates "the ask is too big" with "wrong moment", and only the former should lower the dedication level. And an unattended notification currently sits in the tray forever while new ones pile up behind it, which both trains the user to ignore the tray and leaves triggers unresolved.

Escalating notifications is explicitly not the approach here. The aim is fewer notifications that land better, and to stop spending notifications on moments where they cannot be acted on.

## What

**Activity modes.** The app knows, at fire time, which of three activity modes the user is in: walking, sitting, or transport. The mode is derived from recently observed device activity; when the activity is unknown or the observation is stale, the mode is treated as sitting, since that is the common case. While the user is cycling, no trigger notification fires at all — that state is a suppression, not a mode.

**Habits declare the modes they support.** A habit supports any mode, or a specific subset of walking, sitting and transport. A habit only becomes eligible when the current mode is one it supports; supporting any mode means the mode never gates it. This sits alongside the existing location and time-window eligibility rather than replacing any of it. Existing habits support any mode, so behaviour is unchanged until the user says otherwise.

**Notification text varies by shape.** Every generated variant has a shape — a question, a flat statement, a small challenge, an observation about the present moment, a very short form, a time-boxed form. Selection rotates shapes rather than drawing text at random, so consecutive notifications for the same habit differ structurally and not only lexically. The widget draws from the same pool and shows the same shape variety rather than one fixed phrasing.

**Notification text varies by mode.** Variants are tagged with the modes they suit, and some are mode-neutral. Selection prefers a variant matching the current mode and falls back to a mode-neutral one when none matches, so a habit always has something to say. Generation produces a spread across both shape and mode.

**Variant pools regenerate on a version change.** A server-side generation version is exposed to the app. When it differs from the version a habit's pool was generated under, that pool is regenerated in full. The existing variants remain in use until the replacement batch has landed, so no habit is ever left with nothing to fire. Bumping the version is what rolls out a new generation model or prompt.

**A Later action.** Trigger notifications offer Later alongside Did it and Dismiss. Later resolves the trigger without counting as a dismissal: it does not affect the dedication level and does not feed the consecutive-dismissal demotion. The habit returns to the ordinary eligible pool under its normal cooldown and normal selection weighting — Later grants no priority and no boost, and the habit is not re-offered ahead of anything else. Other habits remain free to be offered in the meantime.

**One live notification at a time.** Before posting a trigger notification, any outstanding unresolved trigger notification is removed and its trigger resolved as expired. Expiry is its own outcome, distinct from dismissal: it resolves the trigger and is visible in history, but it never lowers a dedication level, because inattention is not evidence that the ask was too big. An outcome already recorded is never overwritten.

## Issues

_None yet._