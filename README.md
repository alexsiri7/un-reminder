# The Un-Reminder

A native Android app that replaces fixed-time reminders with **stochastic, context-aware, AI-generated habit prompts** — designed to defeat notification blindness.

Built fully on-device. No backend, no cloud, no account. Works offline. Optional in-app feedback submits annotated screenshots to GitHub (user-initiated only).

---

## 1. Core Objectives

1. **Decouple time from habit** — move away from "Do X at 7:00 PM."
2. **Defeat habituation** — every notification looks and reads differently, so the brain doesn't filter them out. Notification titles rotate through 20 emoji keyed on the trigger ID, so each prompt has a distinct visual signature.
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
| Local storage | **Room** (SQLite) + **DataStore Preferences** | Room for structured data (habits, triggers, locations). DataStore for simple key/value app preferences (e.g. onboarding state). |
| Scheduling | **WorkManager** + **AlarmManager** (exact alarms) | Stochastic trigger firing inside windows. |
| Geofencing | **Android `GeofencingClient`** (Google Play Services Location API) | Background location; requires `ACCESS_BACKGROUND_LOCATION`. |
| Map UI | **osmdroid** | OpenStreetMap-based map picker for location selection; tiles cached automatically on-device. |
| Notifications | **NotificationManager** (Android 13+ runtime permission) | Native. |
| Crash reporting | **Sentry** (`sentry-android`) | On-device-only gating via blank DSN; no PII, habit content, or location data sent. |
| LLM | **Gemma 3 1B (int4)** on-device via **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android:0.10.2`). Model downloaded on first launch (`ModelDownloadWorker`). | Zero-cost after download, offline, private, low-latency. GPU-accelerated on supported devices (OpenCL / vndksupport). |
| Network | **OkHttp** | HTTP client for GitHub feedback API (optional in-app feedback feature). |
| Error reporting | **Sentry Android SDK** (`sentry-android 7.14.0`) | Automatic exception capture for LLM subsystem errors in release builds; opt-in via `SENTRY_DSN` build-config field. No PII, no performance tracing. |
| DI | Hilt | |
| Testing | JUnit + Compose UI tests | |
| CF Worker | **Hono** on **Cloudflare Workers** | Remote LLM generation via Requesty.ai. Handles auth, spend cap, and parallel Gemini Flash fan-out. Deployed via Wrangler. |

**Target device for MVP:** Any Android device with min SDK 31. Model is downloaded on first launch; GPU backend is optional (app falls back to CPU if OpenCL/vndksupport are absent).
**Post-MVP:** Surface download progress to the user; retry/cancel UI.

### Cloudflare Worker (`worker/`)

Runs as a Cloudflare Worker (Hono framework). Exposes two routes:

| Route | Auth | Description |
|---|---|---|
| `GET /v1/health` | Public | Returns `{ status, spendUsedToday, spendUsedMonth, capDaily, capMonthly }` |
| `POST /v1/generate/batch` | `X-UR-Secret` header | Accepts `{ habits[], count? }`, fans out to Requesty (Gemini Flash), returns `{ variants[], spendDollars }` |

**Local dev:**
```sh
cd worker
wrangler dev
```

**Deploy:**
```sh
wrangler secret put UR_SECRET
wrangler secret put REQUESTY_API_KEY
wrangler kv namespace create SPEND_KV  # copy the returned ID into worker/wrangler.toml
wrangler deploy
```

### Build Configuration / GitHub Secrets

The following repository secrets are required for CI release builds:

| Secret | Purpose | Format |
|---|---|---|
| `KEYSTORE_*` / `KEY_*` | APK signing | See Android release signing docs |
| `GITHUB_FEEDBACK_TOKEN` | In-app feedback submission | GitHub PAT with `issues:write` scope |
| `SENTRY_DSN` | Automated crash reporting (optional — blank value disables Sentry) | Sentry DSN URL, e.g. `https://key@org.ingest.sentry.io/projectid` |

All secrets are optional in the sense that the app compiles and runs without them; missing secrets disable the corresponding feature at runtime.

### Worker Secrets (Wrangler)

The following must be set via `wrangler secret put` before deploying the CF Worker:

| Secret / Config | Purpose | How to set |
|---|---|---|
| `UR_SECRET` | Shared auth secret validated in `X-UR-Secret` header | `wrangler secret put UR_SECRET` |
| `REQUESTY_API_KEY` | Requesty.ai API key for Gemini Flash calls | `wrangler secret put REQUESTY_API_KEY` |
| `SPEND_KV` namespace ID | KV namespace for spend tracking | `wrangler kv namespace create SPEND_KV` → paste ID into `worker/wrangler.toml` |

`SPEND_CAP_DAILY_USD` (default `0.50`) and `SPEND_CAP_MONTHLY_USD` (default `5.00`) are plain vars in `worker/wrangler.toml` and can be edited directly.

---

## 4. Core Concepts

### Habit
A repeatable thing the user wants to do. Each habit has:
- `id`
- `name` — user-facing, short (e.g. "meditation", "gratefulness", "singing practice").
- `full_description` — the full version (e.g. "20-minute guided meditation").
- `low_floor_description` — the minimum-viable version (e.g. "3 deep breaths"). **Completing this counts as a win.**
- `locations` — zero or more named `Location` records associated via the `habit_location` junction table. A habit with **no** associated locations is eligible everywhere ("Anywhere" semantics). A habit with one or more locations is only eligible when the user is at one of those locations.
- `active` — boolean. Inactive habits are never selected. Can be toggled manually in the habit editor.
  The system also sets this to `false` automatically after 3 consecutive `DISMISSED` triggers
  (see [Trigger Logic §5](#5-trigger-logic)).
- `created_at`, `updated_at`.

### HabitLocationCrossRef
A many-to-many join between `Habit` and `Location`. Each row has:
- `habit_id` — FK → `habits.id` (CASCADE DELETE)
- `location_id` — FK → `locations.id` (CASCADE DELETE)

Composite primary key `(habit_id, location_id)`.

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

### Location
A named geofence the user has registered. Each location has:
- `id`
- `name` — user-defined label (e.g. "Home", "Gym", "Office").
- `lat`, `lng` — coordinates captured at registration time (current GPS).
- `radius_m` — geofence radius (default 100 m).

### Location state
In-memory set of `location_id` values for the geofences the user is currently inside,
updated by geofence `ENTER`/`EXIT` callbacks. Empty set means no known location.

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
   - Has **no** entries in `habit_location` (eligible everywhere), OR has at least one
     `location_id` matching a geofence the user is currently inside.
   - Not fired within the last N minutes (configurable, default 90m) to avoid tight repeats.
3. Pick **one** habit by weighted-random selection from the eligible set, biased toward habits
   not recently prompted. Weight formula: `1 + min(minutesSince, 1440) / 120`, where
   `minutesSince` is minutes since the habit was last fired (cap: 1440 min = 24 h). A habit
   never fired receives the maximum weight (~13×). If the eligible set is empty, skip silently.
4. Call **Gemma 3 1B (int4, via LiteRT-LM)** with a structured prompt (see below) to generate a fresh, actionable one-liner for this habit instance.
5. Post the notification with the generated text. Action buttons: **Did the full version**, **Did the low-floor**, **Dismiss**.
6. Record the trigger row with the generated prompt and the outcome when the user responds.
   If the last 3 triggers for the same habit are all `DISMISSED`, the habit is automatically set to
   `active = false` (auto-paused). The user can re-activate it by editing the habit.

### LLM prompt shape (on-device Gemma 3 1B)

```
System: You are generating a one-line notification that nudges the user
to do a habit. Make it warm, specific, and varied — never repeat the
exact wording across calls. Maximum 80 characters. Plain text only.

Habit: {name}
Full version: {full_description}
Low-floor version: {low_floor_description}
Current location: {location name(s) or "Anywhere"}
Time of day: {morning|afternoon|evening|night}
```

Expected output example for *gratefulness*:
- "Reflect on 3 small wins from today."
- "Who deserves a mental thank-you right now?"
- "Name one thing that went better than expected today."

### Fallback
If Gemma 3 1B inference fails or takes >5s, fall back to a static template: *"{name}: {low_floor_description}"*. Note: the AI autofill and notification preview features throw on failure (no silent fallback) so the UI can surface a clear error message.

### Habit-field autofill prompt (on-device Gemma 3 1B)

Used to generate `full_description` and `low_floor_description` when the user taps "Autofill with AI" in the Habit editor. Takes only the habit title as input.

```
System: You are generating habit description fields for a productivity app.
Given only a habit title, produce exactly two lines:
Full: <one sentence, specific full description, max 100 chars>
Low-floor: <minimum viable version, max 60 chars>
Plain text only.

Habit title: {name}
```

Expected output format (two lines, no extras):
```
Full: 20-minute guided body-scan meditation
Low-floor: 3 deep breaths
```

Parsed via `lines().firstOrNull { it.startsWith("Full:") }` / `"Low-floor:"`. Throws `IllegalStateException` if either line is missing.

---

## 6. Screens (MVP)

1. **Home screen** — list of habits. FAB → add habit. Tap habit → edit.
2. **Habit editor** — name, full description, low-floor description, location chips (multi-select from saved locations; no selection = "Anywhere"), active toggle.
   AI-assist row: **Autofill with AI** (fills description fields from the habit name via on-device LLM; enabled when name ≥ 2 chars) · **Preview notification** (generates a sample notification text in a dialog; enabled when all description fields are filled).
3. **Windows screen** — list of windows. FAB → add window. Tap → edit.
4. **Window editor** — start/end time pickers, days-of-week chips, frequency slider (1–3), active toggle.
5. **Locations screen** — dynamic list of named locations (any label, not restricted to HOME/WORK).
   FAB opens the map picker; each list item has an Edit button. Map picker shows a full-screen
   osmdroid map with a draggable pin and a bottom sheet for name, radius (50–500 m), and Save.
   Location is stored as lat/lng + radius; osmdroid caches tiles automatically (no offline pre-caching UI).
   Habits link to zero or more locations via the `habit_location` junction table; no selection means "Anywhere".
6. **Recent triggers screen** — last 20 fired triggers with their generated prompts and outcomes. Read-only. Includes a "Send Feedback" button in the top bar.
7. **Settings screen** — notification permission status, background location permission status, a manual "Test trigger now" button, a button to regenerate tomorrow's scheduled triggers, and a "Send Feedback" button.
8. **Onboarding screen** — shown once on first launch. Walks the user through three collapsible steps: (1) granting Notifications and Location permissions, (2) creating a first habit with name/descriptions and weekday schedule, (3) creating a first time window. Includes a "Skip" action in the top bar. Completion (or skip) is persisted via DataStore (`onboarding_done` key) and never shown again. Bottom navigation bar is hidden while onboarding is active.
9. **Feedback screen** — annotated screenshot tool. Captures the current screen, lets the user draw annotations (red/yellow/green strokes), type a description, and submit as a GitHub issue. Falls back to an offline queue (WorkManager) when connectivity is unavailable.

---

## 7. Permissions

- `POST_NOTIFICATIONS` (Android 13+)
- `ACCESS_FINE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION` (requested separately after fine location grant, with clear in-app explanation of why)
- `SCHEDULE_EXACT_ALARM` (Android 12+)
- `FOREGROUND_SERVICE` for the geofence service if needed.
- `INTERNET` — three purposes: (1) map tile downloads for the location picker (OpenStreetMap; no personal data transmitted); (2) optional in-app feedback upload to GitHub (user-initiated; sends annotated screenshot and description); and (3) crash report uploads to Sentry (no personal information, habit content, or location data included).

---

## 8. Database Schema (Room)

```kotlin
// DB version 4
@Entity Habit(id, name, full_description, low_floor_description, active, created_at, updated_at)
@Entity Window(id, start_time, end_time, days_of_week_bitmask, frequency_per_day, active)
@Entity Location(id, name /* user-defined, e.g. "Home", "Gym", "Office" */, lat, lng, radius_m)
@Entity HabitLocationCrossRef(habit_id → Habit.id CASCADE, location_id → Location.id CASCADE)  // junction
@Entity Trigger(id, window_id?, habit_id?, scheduled_at, fired_at?, status, generated_prompt?)
@Entity PendingFeedback(id, screenshot_path? /* nullable */, description, queued_at)  // offline upload queue
```

---

## 9. MVP Acceptance Criteria

The app is considered MVP-complete when:

1. User can CRUD habits, windows, and locations in the UI.
2. Daily schedule job correctly populates `Trigger` rows for the next 24h based on active windows.
3. At least one window trigger fires during its window with a Gemma 3 1B-generated prompt.
4. Entering the registered `HOME` geofence triggers exactly one notification 5 minutes later (debounced).
5. Notification actions correctly record `COMPLETED_FULL`, `COMPLETED_LOW_FLOOR`, or `DISMISSED`.
6. Recent triggers screen displays the last 20 triggers with their generated prompts.
7. App works with airplane mode on (no network dependency).
8. Cold-start to habit list is under 1 second.

---

## 10. Success Metric (personal, not in-app)

> The user completes at least one habit (full or low-floor) on **≥5 days per rolling 7-day window**, measured 2 weeks after starting daily use.

Tracked manually by glancing at the Recent triggers screen. Not a feature.

---

## 11. Out of Scope for MVP (explicit v0.2+ backlog)

- Cloud sync & multi-device (would re-introduce Supabase + Google OAuth).
- iOS version.
- A "Surprise Me" quick-pick screen.
- Multi-modal habits (image/audio prompts from future multimodal models).
- Tasker/IFTTT webhooks for external triggers.
- Habit templates / suggested habits library.

---

## 12. Risks & Open Questions

- **LiteRT-LM GPU acceleration on target device** — verify OpenCL path; app falls back to CPU automatically via `libvndksupport.so`/`libOpenCL.so` `required="false"` manifest entries.
- **Background location reliability** — Android is aggressive about killing background geofence receivers on some OEMs. Pixel stock ROM is the friendliest; still needs a foreground service fallback if misses are frequent.
- **Exact alarms under Doze** — `setExactAndAllowWhileIdle` should be reliable for our use case; verify during dev.
- **Gemma 3 1B int4 latency on-device** — must stay under 5s for notification generation; fallback path handles slow cases.
