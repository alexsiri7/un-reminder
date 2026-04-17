# The Un-Reminder

A native Android app that replaces fixed-time reminders with **stochastic, context-aware, AI-generated habit prompts** — designed to defeat notification blindness.

Built fully on-device. No backend, no cloud, no account. Works offline.

---

## 1. Core Objectives

1. **Decouple time from habit** — move away from "Do X at 7:00 PM."
2. **Defeat habituation** — every notification looks and reads differently, so the brain doesn't filter them out.
3. **Align with energy & context** — only prompt habits the user can actually do in their current location/state.
4. **Zero-friction adoption** — install once, add a few habits, done. No companion setup, no OS-level automation configuration.

---

## 2. Target User (v0.1)

Solo user (the author). Single-device, single-user. Personal productivity / wellbeing tool.

**Explicit non-goals for MVP:**
- No multi-user, no sign-in, no social features.
- No calendar integration.
- No cross-device sync. (If/when added, it becomes a v0.2+ migration.)
- No dashboards, analytics, or streaks. Success is measured manually by the user.

---

## 3. Platform & Stack

| Layer | Choice | Rationale |
|---|---|---|
| Platform | **Android native** (min SDK 31, target 34+) | Background geofencing, reliable notifications, on-device LLM support. |
| Language | **Kotlin** | |
| UI | **Jetpack Compose** + Material 3 | |
| Local storage | **Room** (SQLite) | Local-first. |
| Scheduling | **WorkManager** + **AlarmManager** (exact alarms) | Stochastic trigger firing inside windows. |
| Geofencing | **Android `GeofencingClient`** (Google Play Services Location API) | Background location; requires `ACCESS_BACKGROUND_LOCATION`. |
| Notifications | **NotificationManager** (Android 13+ runtime permission) | Native. |
| LLM | **Gemma 4 E2B on-device** via **ML Kit GenAI Prompt API** / **AICore** (Pixel 8 Pro is AICore-supported). | Zero-cost, offline, private, low-latency. |
| DI | Hilt | |
| Networking | **OkHttp** | Feedback upload to GitHub API. |
| Testing | JUnit + Compose UI tests | |

**Target device for MVP:** Pixel 8 Pro (AICore available, Gemma 4 runs natively).
**Fallback:** bundle ML Kit GenAI Prompt API for devices without AICore (post-MVP).

### 3.1 Building Locally

Requires **Java 17 or 21** (not 11 — ML Kit GenAI compile dependency requires 17+).

To enable feedback submission, create `local.properties` (git-ignored) and add:
```
github.feedback.token=ghp_your_fine_grained_pat_here
```
The PAT needs `contents:write` and `issues:write` on this repo. Without it, the feedback button shows a Snackbar and no network calls are made.

---

## 4. Core Concepts

### Habit
A repeatable thing the user wants to do. Each habit has:
- `id`
- `name` — user-facing, short (e.g. "meditation", "gratefulness", "singing practice").
- `full_description` — the full version (e.g. "20-minute guided meditation").
- `low_floor_description` — the minimum-viable version (e.g. "3 deep breaths"). **Completing this counts as a win.**
- `location_tag` — one of `{HOME, WORK, COMMUTE, ANYWHERE}`. Default `ANYWHERE`.
- `active` — boolean. Inactive habits are never selected.
- `created_at`, `updated_at`.

### Window
A user-defined time range during which stochastic triggers may fire. Each window has:
- `id`
- `start_time` — local time-of-day (e.g. 18:00).
- `end_time` — local time-of-day (e.g. 21:00).
- `days_of_week` — subset of Mon–Sun.
- `frequency_per_day` — integer; how many triggers to fire in this window (1–3).
- `active`.

### Trigger
A scheduled notification event.
- `id`
- `window_id` (nullable — arrival triggers have no window)
- `habit_id` (populated at fire time, not schedule time — see below)
- `scheduled_at` — timestamp.
- `fired_at` — nullable.
- `status` — `{SCHEDULED, FIRED, COMPLETED_FULL, COMPLETED_LOW_FLOOR, DISMISSED}`.
- `generated_prompt` — the AI-generated text actually shown in the notification.

### Location state
A simple in-memory / lightweight-persisted state of the user's current location tag (`HOME`, `WORK`, `COMMUTE`, or unknown), updated by geofence transitions.

---

## 5. Trigger Logic

### Two kinds of triggers

**(A) Window triggers — stochastic, scheduled daily.**
- Every night at 00:05 local time, a daily job runs and, for each active window, picks `frequency_per_day` random timestamps uniformly distributed within `[start_time, end_time]` on eligible days.
- Each timestamp is registered as an exact `AlarmManager` alarm.
- When the alarm fires, the fire-time pipeline runs (see below).

**(B) Arrival triggers — event-driven.**
- Android geofence callbacks fire on `ENTER` / `EXIT` events for registered locations (`HOME`, `WORK`).
- On `ENTER HOME` (and similarly for `WORK`), the app schedules **one** notification **5 minutes later** (user has time to settle in). This is a single one-shot alarm.
- Debounced: at most one arrival trigger per location per 30 minutes.

### Fire-time pipeline (identical for both trigger kinds)

1. Resolve current location state.
2. Query eligible habits:
   - `active = true`
   - `location_tag` matches current state, OR `location_tag = ANYWHERE`.
   - Not fired within the last N minutes (configurable, default 90m) to avoid tight repeats.
3. Pick **one** habit uniformly at random from eligible set. If the set is empty, skip silently.
4. Call Gemma 4 E2B with a structured prompt (see below) to generate a fresh, actionable one-liner for this habit instance.
5. Post the notification with the generated text. Action buttons: **Did the full version**, **Did the low-floor**, **Dismiss**.
6. Record the trigger row with the generated prompt and the outcome when the user responds.

### LLM prompt shape (on-device Gemma 4)

```
System: You are generating a one-line notification that nudges the user
to do a habit. Make it warm, specific, and varied — never repeat the
exact wording across calls. Maximum 80 characters. Plain text only.

Habit: {name}
Full version: {full_description}
Low-floor version: {low_floor_description}
Current location: {HOME|WORK|COMMUTE|ANYWHERE}
Time of day: {morning|afternoon|evening|night}
```

Expected output example for *gratefulness*:
- "Reflect on 3 small wins from today."
- "Who deserves a mental thank-you right now?"
- "Name one thing that went better than expected today."

### Fallback
If Gemma 4 inference fails or takes >5s, fall back to a static template: *"{name}: {low_floor_description}"*.

---

## 6. Screens (MVP)

1. **Home screen** — list of habits. FAB → add habit. Tap habit → edit.
2. **Habit editor** — name, full description, low-floor description, location tag, active toggle.
3. **Windows screen** — list of windows. FAB → add window. Tap → edit.
4. **Window editor** — start/end time pickers, days-of-week chips, frequency slider (1–3), active toggle.
5. **Locations screen** — "Set my Home" / "Set my Work". Uses current GPS at capture time; stores lat/lng + radius (default 100m). Re-settable.
6. **Recent triggers screen** — last 20 fired triggers with their generated prompts and outcomes. Read-only. FAB opens the Feedback screen.
7. **Settings screen** — notification permission status, background location permission status, a manual "Test trigger now" button, a button to regenerate tomorrow's scheduled triggers, and a "Send Feedback" button.
8. **Feedback screen** — captures an annotated screenshot (PixelCopy + canvas overlay with color picker and clear-all eraser), accepts a text description, and submits as a GitHub Issue via the Contents + Issues API. Queues locally when offline; WorkManager retries on reconnection.

No onboarding flow for MVP beyond permission requests on first launch. User is expected to add habits and windows themselves.

---

## 7. Permissions

- `POST_NOTIFICATIONS` (Android 13+)
- `ACCESS_FINE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION` (requested separately after fine location grant, with clear in-app explanation of why)
- `SCHEDULE_EXACT_ALARM` (Android 12+)
- `FOREGROUND_SERVICE` for the geofence service if needed.
- `INTERNET` — for feedback submission to GitHub API (WorkManager, queued when offline).

---

## 8. Database Schema (Room)

**DB version**: 2 (MIGRATION_1_2 adds `pending_feedback` table)

```kotlin
@Entity Habit(id, name, full_description, low_floor_description, location_tag, active, created_at, updated_at)
@Entity Window(id, start_time, end_time, days_of_week_bitmask, frequency_per_day, active)
@Entity Location(id, label /* HOME|WORK */, lat, lng, radius_m)
@Entity Trigger(id, window_id?, habit_id?, scheduled_at, fired_at?, status, generated_prompt?)
@Entity PendingFeedback(id, screenshot_path, description, created_at)  // DB v2 — offline feedback queue
```

---

## 9. MVP Acceptance Criteria

The app is considered MVP-complete when:

1. User can CRUD habits, windows, and locations in the UI.
2. Daily schedule job correctly populates `Trigger` rows for the next 24h based on active windows.
3. At least one window trigger fires during its window with a Gemma 4-generated prompt.
4. Entering the registered `HOME` geofence triggers exactly one notification 5 minutes later (debounced).
5. Notification actions correctly record `COMPLETED_FULL`, `COMPLETED_LOW_FLOOR`, or `DISMISSED`.
6. Recent triggers screen displays the last 20 triggers with their generated prompts.
7. Core habit delivery (triggers, notifications, AI prompts) works with airplane mode on — no network dependency for the main loop.
8. Feedback submission queues locally when offline and uploads automatically when network is available (WorkManager retry).
9. Cold-start to habit list is under 1 second.

---

## 10. Success Metric (personal, not in-app)

> The user completes at least one habit (full or low-floor) on **≥5 days per rolling 7-day window**, measured 2 weeks after starting daily use.

Tracked manually by glancing at the Recent triggers screen. Not a feature.

---

## 11. Out of Scope for MVP (explicit v0.2+ backlog)

- Emoji/icon shuffling on notifications.
- Dismissal tracking → auto-inactivate after 3 consecutive dismissals.
- Cloud sync & multi-device (would re-introduce Supabase + Google OAuth).
- iOS version.
- A "Surprise Me" quick-pick screen.
- Weighted habit selection based on history.
- Multi-modal habits (image/audio prompts from Gemma 4 multimodal).
- Tasker/IFTTT webhooks for external triggers.
- Habit templates / suggested habits library.

---

## 12. Risks & Open Questions

- **AICore availability for Gemma 4 E2B on Pixel 8 Pro** — confirmed in developer preview; verify it runs end-to-end before wiring the fallback.
- **Background location reliability** — Android is aggressive about killing background geofence receivers on some OEMs. Pixel stock ROM is the friendliest; still needs a foreground service fallback if misses are frequent.
- **Exact alarms under Doze** — `setExactAndAllowWhileIdle` should be reliable for our use case; verify during dev.
- **Gemma 4 E2B latency on-device** — must stay under 5s for notification generation; fallback path handles slow cases.
