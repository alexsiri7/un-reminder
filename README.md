# The Un-Reminder

A reminder app designed to defeat notification blindness by decoupling tasks from fixed times and matching prompts to context and energy.

## 1. Core Objectives

- **Decouple Time from Task** — move away from "Do X at 7:00 PM."
- **Defeat Habituation** — keep notifications novel so the brain doesn't filter them out.
- **Align with Energy** — only show tasks the user can actually do in their current context.

## 2. Functional Requirements

### A. The Task "Menu" System

Tasks are not binary (Done / Not Done). Each task entry has:

- **Full Version** — e.g. 20 min meditation.
- **Low-Floor Version** — e.g. 3 deep breaths.
- **Location Tags** — Home, Commute, Office.

### B. Trigger Logic

- **Stochastic (random) triggers** — user defines a *window* (e.g. 6:00–9:00 PM); the app picks a random time within it.
- **Geofence integration** — background GPS is restricted in PWAs, so use *arrival logic*: opening the app or hitting an "I'm Home" button (e.g. via iOS Shortcut POSTing to an API) unlocks the home-context menu.

### C. Anti-Blindness UI

- **Emoji shuffling** — notification icon and text rotate through a library so the visual profile changes every time.
- **Mystery prompts** — notification says "Check the Menu" instead of naming the task. Forces a micro-interaction, preventing dismiss-blindness.

## 3. Technical Architecture

| Component | Technology |
|---|---|
| Frontend | Next.js (React) with PWA capabilities for "Add to Home Screen" |
| Backend | Supabase (PostgreSQL + Edge Functions) |
| Notifications | Web Push API (VAPID keys) — system-level alerts even when browser is closed |
| Randomizer | Cron (GitHub Actions or Supabase Edge) — calculates a random timestamp daily per active window |

## 4. Database Schema

| Table | Fields | Purpose |
|---|---|---|
| `Tasks` | `id, user_id, title, low_floor_desc, location_tag` | Task variants |
| `Windows` | `id, user_id, start_time, end_time, frequency_per_day` | When random triggers are allowed |
| `Triggers` | `id, task_id, scheduled_at, status (sent/dismissed/completed)` | Logs the randomized schedule |
| `Locations` | `id, user_id, lat, lng, radius, label (Home/Work)` | Geofence coordinates |

## 5. The "Tube to Home" Bridge — Logic Flow

1. **Detection** — user is on the Tube (commute context).
2. **Activation** — user reaches their home geofence.
3. **Trigger** — iOS Shortcut / Android Automation sends `POST /api/arrival?user_id=123`.
4. **Response** — server schedules a Web Push Notification 5 minutes later (time to get inside).
5. **Interaction** — tapping the notification opens a Quick-Select screen with 3 tasks: singing, meditation, and a "Surprise Me" button.

## 6. Success Metrics for Task Aversion

- **Friction Score** — did the user do the *Low-Floor* version? Counts as a win.
- **Dismissal Tracking** — if a task is dismissed 3 times in a row, auto-move it to *Inactive* and prompt the user to rewrite the Low-Floor version.
