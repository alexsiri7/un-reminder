---
created: '2026-09-10'
github_issue: 290
id: '016'
status: idea
title: 'Pull-based nudging: doable-now menu, growth counters, evening invitation,
  widget'
updated: '2026-09-10'
---

## Why

The notifications are being swiped away without being read, so the app's core loop has stopped working. Two distinct failures are tangled together. First, a notification often isn't attended to at all — and because swipe-away is currently untracked, that produces no signal anywhere in the app. Second, when a notification is read, it offers exactly one habit as a yes/no, so "not that one, but I would do something else" has no way to be expressed and gets recorded as a refusal.

Meanwhile nothing rewards doing anything at all. The only behavioural feedback loop that exists ends in auto-pausing the habit, which is the opposite of helping someone do it.

The app needs surfaces the user can pull from when they have a moment, a win small enough to always be available, and progress numbers that only ever go up. Making the day the unit of success — "you did something today" — rather than per-habit consistency is what lets a menu of choices work: any of the three options on offer moves the same number.

## What

**A doable-now menu.** A surface that offers 3 habits the user could act on right now, drawn at random from those currently eligible. A "load more" control draws 3 more from the remaining eligible habits, and stops offering itself once the eligible set is exhausted. When nothing is eligible, the surface says why rather than showing an empty list. Completing a habit from the menu counts exactly as completing it from a notification: same effect on daily limits, cooldowns, dedication level and counters.

**Three growth counters.** Numbers that only ever increase, never reset, and are never broken by a day where nothing happened: (1) the number of days on which any habit was completed, (2) per habit, the number of days on which that habit was completed, (3) per habit, the total number of times it has been completed. Every completion, from any surface, moves the first counter for the day and both of that habit's counters. There are no streaks, no freezes, and no penalty for an empty day. The overall counter is visible on the menu; the per-habit pair is visible on the habit.

**An evening invitation.** On a day where nothing has been completed yet, a single notification late in the day invites the user to make the day count and opens the menu. It offers to show the menu or to decline for tonight. Declining it does not count as dismissing any habit and does not affect any dedication level. At most one such notification per day.

**A home-screen widget.** Shows one currently-doable habit and a single action to mark it done, plus a tap target that opens the menu. Its content refreshes at least every 30 minutes, and also whenever the user's context changes in a way that alters what is doable. When nothing is doable it shows a resting state rather than a stale suggestion.

**Swiping a notification is a dismissal.** Swiping a trigger notification away is recorded as a dismissal rather than leaving the trigger unresolved forever.

**Habits are never auto-paused.** Repeated dismissal lowers the dedication level, which lowers the size of the ask. A habit already at level 0 stays at level 0 and stays available indefinitely. Nothing in the app deactivates a habit on the user's behalf or tells them a habit has been stopped.

## Issues

- #290 — Remove habit auto-pause; delete dead DedicationLevelManager promotion path
- #291 — Record notification swipe-away as a dismissal
- #292 — Growth counters: days-with-any-completion, per-habit days, per-habit total
- #293 — Doable-now menu screen: 3 random eligible habits with load more
- #294 — Evening invitation notification on days with no completions
- #295 — Home-screen widget: one doable habit, one action, 30-minute refresh