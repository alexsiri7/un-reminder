---
created: '2026-09-15'
github_issue: null
id: 019
status: draft
title: 'Multi-tenant Worker: per-user tokens with salted-hash storage and per-user
  spend caps'
updated: '2026-09-24'
---

## Why

Alex wants to share un-reminder with friends. The Worker currently authenticates every caller against one shared secret (`X-UR-Secret` compared against `UR_SHARED_SECRET`) and enforces one global spend cap (`UR_DAILY_CAP_CENTS`, `UR_MONTHLY_CAP_CENTS`). Neither survives a second user.

The shared secret would ship inside every friend's APK, so extracting it from one install grants anyone unlimited access to the generation endpoint — and rotating it breaks every install at once. The global cap is worse in practice: one friend pressing "regenerate all variants" can exhaust the day's budget and silently stop everyone else's notifications from having anything new to say. A `402` is returned, so the app fails quietly rather than visibly.

The personalisation layer turns out not to be the obstacle. `personalContext` is already a user-editable free-text field stored on the device and passed to the Worker, the generation prompt is fully parameterised over habit, tags, location, time of day and modes, and the mascot catalogue is a generic costumed-cat set rather than anything specific to Alex. Habits, locations, windows and ladders are all user data with onboarding already in place. So the work genuinely reduces to making the Worker multi-tenant.

Salted hashing is used here for storage, not transit. A client must possess whatever it presents, so hashing on the device would simply make the hash the new shared secret. HMAC request signing would keep the key off the wire entirely but requires the Worker to hold each key in recoverable form, losing the at-rest protection. For a handful of users over TLS, protecting the stored credential is the better trade and much less to build.

## What

**Per-user tokens replace the shared secret.** Each user of the app holds their own high-entropy random token, presented on every Worker request over TLS. The Worker stores only a salted hash of each token, so a disclosure of its storage yields nothing usable. Token comparison remains timing-safe. The existing single `UR_SHARED_SECRET` path is removed once tokens are in place, not left as a fallback.

**Tokens are provisioned manually.** There is no signup flow, no account, and no identity. Alex mints a token for each friend out of band and they enter it in the app. Self-registration was deferred at the time; req 020 now specifies it, using the Play Integrity verdict this requirement introduced as the proof of a legitimate install.

**Spend caps become per-user.** The daily and monthly generation caps apply to each token separately rather than to the Worker as a whole, so one user regenerating their pools cannot exhaust anyone else's budget or silence their notifications. A global cap remains on top as a backstop against the total bill, but hitting a per-user cap must affect only that user.

**A user hitting their cap is told so.** The app distinguishes "your generation budget is spent" from "the service is down" from "your token is wrong", because the remedies are completely different and a silent failure looks identical to all three.

**Play Integrity as a second gate.** Worker requests additionally carry a Play Integrity token, which the Worker verifies before generating. This does not identify a user — it confirms the call comes from an unmodified, Play-recognised build by a licensed installer, so an extracted per-user token cannot be used from anything else. It is an additional check alongside the per-user token, never a replacement for it, and the app must degrade sensibly when integrity checking is unavailable rather than becoming unusable.

**Nothing else becomes multi-user.** Habits, variants, locations, windows, outcomes and growth counters all remain local to the device. There is no server-side user data beyond a token hash and a spend counter, and no sync, sharing or account recovery. This requirement makes the *Worker* multi-tenant; the app stays single-user and local-first.

## Issues

_None yet._