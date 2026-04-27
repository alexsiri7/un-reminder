# The Un-Reminder

A native Android app that replaces fixed-time reminders with **stochastic, context-aware, AI-generated habit prompts** — designed to defeat notification blindness.

Notifications generated via a private cloud proxy (personal Cloudflare Worker + Requesty.ai). No third-party account required. Optional in-app feedback submits annotated screenshots to GitHub (user-initiated only).

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
| Platform | **Android native** (min SDK 31, target 34+) | Background geofencing, reliable notifications, Cloudflare Worker integration. |
| Language | **Kotlin** | |
| UI | **Jetpack Compose** + Material 3 | |
| Local storage | **Room** (SQLite) + **DataStore Preferences** | Room for structured data (habits, triggers, locations). DataStore for simple key/value app preferences (e.g. onboarding state). |
| Scheduling | **WorkManager** + **AlarmManager** (exact alarms) | Stochastic trigger firing inside windows. |
| Geofencing | **Android `GeofencingClient`** (Google Play Services Location API) | Background location; requires `ACCESS_BACKGROUND_LOCATION`. |
| Map UI | **osmdroid** | OpenStreetMap-based map picker for location selection; tiles cached automatically on-device. |
| Notifications | **NotificationManager** (Android 13+ runtime permission) | Native. |
| Crash reporting | **Sentry** (`sentry-android`) | On-device-only gating via blank DSN; no PII, habit content, or location data sent. |
| LLM | **Gemini Flash** via **Requesty.ai** proxy, deployed as a Cloudflare Worker (`worker/`). Variations pre-generated and stored in Room DB. | Private cloud via personal proxy — no data sent to third parties beyond the proxy owner's account. Low-latency notification delivery from pre-filled pool. |
| Network | **OkHttp** | HTTP client for worker API calls and GitHub feedback API. |
| Error reporting | **Sentry Android SDK** (`sentry-android 7.14.0`) | Automatic exception capture for LLM subsystem errors in release builds; opt-in via `SENTRY_DSN` build-config field. No PII, no performance tracing. |
| DI | Hilt | |
| Testing | JUnit + Compose UI tests | |
| CF Worker | **Hono** on **Cloudflare Workers** | Remote LLM generation via Requesty.ai. Handles auth, spend cap, and parallel Gemini Flash fan-out. Deployed via Wrangler. |

**Target device for MVP:** Any Android device with min SDK 31. Cloud worker URL and secret are configured at build time or at runtime via Cloud AI settings.

### Cloudflare Worker (`worker/`)

Runs as a Cloudflare Worker (Hono framework). Exposes these routes:

| Route | Auth | Description |
|---|---|---|
| `GET /v1/health` | Public | Returns `{ status, spendUsedToday, spendUsedMonth, capDaily, capMonthly }` |
| `POST /v1/generate/batch` | `X-UR-Secret` header | Accepts `{ habitTitle, habitTags, locationName, timeOfDay, n }`, returns `{ variants: string[] }` via Requesty |
| `POST /v1/habit-fields` | `X-UR-Secret` header | Accepts `{ title }`, returns `{ descriptionLadder: string[] }` (6 elements, one per dedication level) via Requesty |
| `POST /v1/preview` | `X-UR-Secret` header | Accepts `{ habit: { title, tags, notes }, locationName }`, returns `{ text }` notification preview |

**Local dev:**
```sh
cd worker
wrangler dev
```

**Deploy:**
```sh
wrangler secret put UR_SHARED_SECRET
wrangler secret put UR_REQUESTY_KEY
wrangler kv namespace create UR_SPEND  # copy the returned ID into worker/wrangler.toml
wrangler deploy
```

### Build Configuration / GitHub Secrets

The following repository secrets are required for CI release builds:

| Secret | Purpose | Format |
|---|---|---|
| `KEYSTORE_*` / `KEY_*` | APK signing | See Android release signing docs |
| `GITHUB_FEEDBACK_TOKEN` | In-app feedback submission | GitHub PAT with `issues:write` scope |
| `SENTRY_DSN` | Automated crash reporting (optional — blank value disables Sentry) | Sentry DSN URL, e.g. `https://key@org.ingest.sentry.io/projectid` |
| `WORKER_URL` | Default URL for cloud AI variant generation worker (optional — configurable at runtime via Cloud AI settings) | Full URL, e.g. `https://un-reminder-worker.yourname.workers.dev` |
| `WORKER_SECRET` | Default shared secret baked into BuildConfig (optional — blank disables default) | Must match worker's `UR_SHARED_SECRET` |

All secrets are optional in the sense that the app compiles and runs without them; missing secrets disable the corresponding feature at runtime.

### Worker Secrets (Wrangler)

The following must be set via `wrangler secret put` before deploying the CF Worker:

| Secret / Config | Purpose | How to set |
|---|---|---|
| `UR_SHARED_SECRET` | Shared auth secret validated in `X-UR-Secret` header | `wrangler secret put UR_SHARED_SECRET` |
| `UR_REQUESTY_KEY` | Requesty.ai API key for Gemini Flash calls | `wrangler secret put UR_REQUESTY_KEY` |
| `UR_SPEND` namespace ID | KV namespace for spend tracking | `wrangler kv namespace create UR_SPEND` → paste ID into `worker/wrangler.toml` |

`UR_DAILY_CAP_CENTS` (default `50`, i.e. $0.50) and `UR_MONTHLY_CAP_CENTS` (default `500`, i.e. $5.00) are plain vars in `worker/wrangler.toml` and can be edited directly. Note the unit is **cents**, not dollars.

---

## 4. Core Concepts

### Habit
A repeatable thing the user wants to do. Each habit has:
- `id`
- `name` — user-facing, short (e.g. "meditation", "gratefulness", "singing practice").
- `dedication_level` — integer 0–5. The user's current commitment level for this habit, auto-promoted
  by `DedicationLevelManager` when completion thresholds are met. Defaults to 0.
- `auto_adjust_level` — boolean. When `true`, `DedicationLevelManager` will auto-promote `dedication_level`
  based on recent completions. Defaults to `true`.
- `locations` — zero or more named `Location` records associated via the `habit_location` junction table. A habit with **no** associated locations is eligible everywhere ("Anywhere" semantics). A habit with one or more locations is only eligible when the user is at one of those locations.
- `active` — boolean. Inactive habits are never selected. Can be toggled manually in the habit editor.
  When `auto_adjust_level` is true, 3 consecutive `DISMISSED` triggers demote `dedication_level` by 1.
  If the habit is already at level 0, 3 consecutive `DISMISSED` triggers set it to `active = false`
  (auto-paused). The user can re-activate it from the habit editor.
  (see [Trigger Logic §5](#5-trigger-logic)).
- `created_at`, `updated_at`.

Per-level descriptive text is stored separately in `HabitLevelDescriptionEntity` (`habit_level_descriptions`
table), keyed by `(habit_id, level)`. Levels 0 and 5 are pre-populated during migration from the old
`low_floor_description` and `full_description` values; levels 1–4 can be filled by the user or AI autofill.

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
- `status` — `{SCHEDULED, FIRED, COMPLETED, DISMISSED}`.
- `generated_prompt` — the AI-generated text actually shown in the notification.

### Location
A named geofence the user has registered. Each location has:
- `id`
- `name` — user-defined label (e.g. "Home", "Gym", "Office").
- `lat`, `lng` — coordinates captured at registration time (current GPS).
- `radius_m` — geofence radius (default 100 m).

### Variation
A pre-generated prompt text for a habit, stored in a local pool to avoid LLM latency at fire time.
- `id`
- `habit_id` — FK → `habits.id` (CASCADE DELETE). Each habit has its own pool.
- `text` — the generated prompt text.
- `prompt_fingerprint` — hash of the LLM prompt that produced this variation; part of the `(habit_id, prompt_fingerprint, text)` composite unique constraint that prevents duplicates.
- `generated_at` — when the variation was generated.
- `consumed_at` — nullable; set when the variation is picked for a notification. Unconsumed variations form the available pool.

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
4. Pick an unused variation from the cloud-generated pool (see Variation entity). If the pool is empty, use the level description fallback (see Fallback below).
5. Post the notification with the generated text. Action buttons: **Did it**, **Dismiss**.
6. Record the trigger row with the generated prompt and the outcome when the user responds.
   When `auto_adjust_level` is true: 3 consecutive `COMPLETED` triggers promote `dedication_level`
   by 1 (up to max 5); 3 consecutive `DISMISSED` triggers demote it by 1. At level 0, 3 consecutive
   `DISMISSED` triggers auto-pause the habit (`active = false`). The user can re-activate via the habit editor.

### Variation pool (cloud-generated)

Notification texts are pre-generated in batches by the Cloudflare Worker (`/v1/generate/batch`) and stored locally in the `Variation` table. At fire time, `TriggerPipeline` picks an unused variation from the pool — no LLM call on the hot path.

When the pool runs low (`VariationRepository.needsRefill`), a `RefillWorker` is enqueued to fetch a fresh batch from the worker.

### Fallback
If the variation pool is empty at fire time, the notification uses the `HabitLevelDescriptionEntity`
text for the habit's current `dedication_level` as the prompt body. If that is also blank, it falls
back to `habit.name`. A refill is enqueued in both cases. AI autofill and notification preview features call the worker directly and throw on failure so the UI can surface a clear error message.

### Habit-field autofill (cloud)

Used to generate all 6 `description_ladder` slots when the user taps "Autofill with AI" in the Habit editor.
Calls the worker's `/v1/habit-fields` endpoint with the habit title; the worker returns
`{ descriptionLadder: string[] }` with one description per dedication level (0–5).

### Notification preview (cloud)

Generates a sample notification via the worker's `/v1/preview` endpoint. Shown in a dialog in the Habit editor.

---

## 6. Screens (MVP)

1. **Home screen** — list of habits. FAB → add habit. Tap habit → edit. BugReport icon in header → Feedback screen.
2. **Habit editor** — name, dedication level progress bar (0–5), auto-adjust toggle, 6-level
   description fields (level 0 = minimum/low-floor, level 5 = full version; current level highlighted),
   location chips (multi-select from saved locations; no selection = "Anywhere"), active toggle.
   AI-assist row: **Autofill with AI** (populates all 6 levels (0–5) from the habit name via cloud AI;
   enabled when name ≥ 2 chars) · **Preview notification** (generates a sample notification text;
   enabled when at least one level description is non-blank).
3. **Windows screen** — list of windows. FAB → add window. Tap → edit. BugReport icon in header → Feedback screen.
4. **Window editor** — start/end time pickers, days-of-week chips, frequency slider (1–3), active toggle.
5. **Locations screen** — dynamic list of named locations (any label, not restricted to HOME/WORK).
   FAB opens the map picker; each list item has an Edit button. Map picker shows a full-screen
   osmdroid map with a draggable pin and a bottom sheet for name, radius (50–500 m), and Save.
   Location is stored as lat/lng + radius; osmdroid caches tiles automatically (no offline pre-caching UI).
   Habits link to zero or more locations via the `habit_location` junction table; no selection means "Anywhere".
6. **Recent triggers screen** — last 20 fired triggers with their generated prompts and outcomes. Read-only. Includes a "Send Feedback" button in the top bar.
7. **Settings screen** — notification permission status, background location permission status, a manual "Test trigger now" button, a button to regenerate tomorrow's scheduled triggers, a link to Cloud AI settings, and a "Send Feedback" button.
7a. **Cloud AI settings screen** — worker URL and shared secret for cloud generation, and a "regenerate all variants" button that clears the variation pool and re-queues a refill job for every active habit.
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
// DB version 7
@Entity Habit(id, name, dedication_level/*Int 0-5*/, auto_adjust_level/*Boolean*/, active, created_at, updated_at)
@Entity HabitLevelDescriptionEntity(habit_id → Habit.id CASCADE, level/*0-5*/, description)  // per-level text
@Entity Window(id, start_time, end_time, days_of_week_bitmask, frequency_per_day, active)
@Entity Location(id, name /* user-defined, e.g. "Home", "Gym", "Office" */, lat, lng, radius_m)
@Entity HabitLocationCrossRef(habit_id → Habit.id CASCADE, location_id → Location.id CASCADE)  // junction
@Entity Trigger(id, window_id?, habit_id?, scheduled_at, fired_at?, status, generated_prompt?)
@Entity PendingFeedback(id, screenshot_path? /* nullable */, description, queued_at)  // offline upload queue
@Entity Variation(id, habit_id → Habit.id CASCADE, text, prompt_fingerprint, generated_at, consumed_at?)  // variation pool
```

---

## 9. MVP Acceptance Criteria

The app is considered MVP-complete when:

1. User can CRUD habits, windows, and locations in the UI.
2. Daily schedule job correctly populates `Trigger` rows for the next 24h based on active windows.
3. At least one window trigger fires during its window with a Gemma 3 1B-generated prompt.
4. Entering the registered `HOME` geofence triggers exactly one notification 5 minutes later (debounced).
5. Notification actions correctly record `COMPLETED` or `DISMISSED`.
6. Recent triggers screen displays the last 20 triggers with their generated prompts.
7. App works with airplane mode on (no network dependency).
8. Cold-start to habit list is under 1 second.

---

## 10. Success Metric (personal, not in-app)

> The user completes at least one habit on **≥5 days per rolling 7-day window**, measured 2 weeks after starting daily use.

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

- **Background location reliability** — Android is aggressive about killing background geofence receivers on some OEMs. Pixel stock ROM is the friendliest; still needs a foreground service fallback if misses are frequent.
- **Exact alarms under Doze** — `setExactAndAllowWhileIdle` should be reliable for our use case; verify during dev.
- **Worker availability** — if the Cloudflare Worker is down or the spend cap is exceeded, AI features degrade gracefully (pool fallback to `habit.name`, UI shows spend-cap snackbar).
