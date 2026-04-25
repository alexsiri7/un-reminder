# Research: Multi-Level Dedication System for Android Habit App

## Overview

This document consolidates technical and behavioral-science research supporting the replacement of the binary low-floor/full habit description system with a **0–5 dedication level** system. Topics covered:

1. Android segmented/discrete progress bar — tappable UI
2. Habit progression psychology (BJ Fogg, Elastic Habits, streak research)
3. Android Room database JSON column storage with TypeConverter
4. Habit ladder behavioral theory
5. Android notification single-action best practices

---

## 1. Android Segmented Progress Bar — Tappable Discrete Steps

### 1.1 Approach Options

Three viable implementation paths exist, ordered from least to most effort:

| Approach | UI Toolkit | Tap Support | Effort |
|---|---|---|---|
| Jetpack Compose `Slider` with `steps` | Compose | Built-in snap | Low |
| Compose `MultiChoiceSegmentedButtonRow` | Compose | Built-in click | Low |
| Custom `View` on `Canvas` | View system | Manual `onTouchEvent` | High |
| Third-party library (`rayzone107/SegmentedProgressBar`) | View system | Partial (no tap) | Medium |

---

### 1.2 Jetpack Compose — Discrete Slider (Recommended)

The `Slider` composable natively supports discrete steps via the `steps` parameter. For a 0–5 range (6 positions), use `steps = 4` (the two endpoints are not counted in `steps`).

```kotlin
@Composable
fun DedicationLevelSlider(
    level: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember(level) { mutableFloatStateOf(level.toFloat()) }

    Column(modifier) {
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                // Commit only when drag ends — avoids excessive DB writes
                onLevelChange(sliderPosition.roundToInt())
            },
            steps = 4,          // 4 interior stops → 6 total positions (0,1,2,3,4,5)
            valueRange = 0f..5f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        Text("Level ${sliderPosition.roundToInt()}")
    }
}
```

**Key API notes:**
- `steps` = number of discrete stops *between* the two endpoints. For 6 positions (0–5), use `steps = 4`.
- `onValueChange` fires continuously during drag; `onValueChangeFinished` fires once on release — use the latter for database writes.
- `valueRange = 0f..5f` combined with `steps = 4` places ticks at exactly 0, 1, 2, 3, 4, 5.

---

### 1.3 Jetpack Compose — Segmented Button Row (Alternative)

`MultiChoiceSegmentedButtonRow` (Material 3) gives a button-row feel where each level is a tappable chip:

```kotlin
@Composable
fun DedicationLevelSegmented(
    level: Int,
    onLevelSelected: (Int) -> Unit
) {
    val levels = 0..5
    SingleChoiceSegmentedButtonRow {
        levels.forEach { l ->
            SegmentedButton(
                selected = l == level,
                onClick = { onLevelSelected(l) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = l,
                    count = levels.count()
                ),
                label = { Text("$l") }
            )
        }
    }
}
```

This gives a clean tap-to-select UX with built-in ripple and selection state, no custom drawing required.

---

### 1.4 Haptic Feedback on Step Transitions

For the slider approach, add haptic ticks when crossing step boundaries — this improves the physical "click" sensation:

```kotlin
val haptic = LocalHapticFeedback.current

Slider(
    value = sliderPosition,
    onValueChange = { newValue ->
        val newStep = newValue.roundToInt()
        val oldStep = sliderPosition.roundToInt()
        if (newStep != oldStep) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        sliderPosition = newValue
    },
    steps = 4,
    valueRange = 0f..5f
)
```

`HapticFeedbackType.SegmentFrequentTick` (API 26+) is specifically designed for slider ticks — subtle but confirmatory.

---

### 1.5 Custom Canvas View — Tappable Segments (View System)

If the app still targets the legacy `View` system, a custom `View` that draws segments on `Canvas` and handles taps is needed. Core pattern:

```kotlin
class DedicationLevelBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var levelCount: Int = 6           // 0..5 = 6 segments
    var currentLevel: Int = 0
        set(value) { field = value; invalidate() }
    var onLevelSelected: ((Int) -> Unit)? = null

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6200EE")
        style = Paint.Style.FILL
    }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        style = Paint.Style.FILL
    }

    // Pre-allocated RectF list — never allocate inside onDraw()
    private val segmentRects = mutableListOf<RectF>()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        segmentRects.clear()
        val gapWidth = h / 3f
        val segWidth = (w - gapWidth * (levelCount - 1)) / levelCount
        for (i in 0 until levelCount) {
            val left = i * (segWidth + gapWidth)
            segmentRects.add(RectF(left, 0f, left + segWidth, h.toFloat()))
        }
    }

    override fun onDraw(canvas: Canvas) {
        segmentRects.forEachIndexed { index, rect ->
            // Segments 0..currentLevel are "active"
            val paint = if (index <= currentLevel) activePaint else inactivePaint
            canvas.drawRoundRect(rect, 8f, 8f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // Find which segment was tapped using RectF.contains()
            val tappedLevel = segmentRects.indexOfFirst { it.contains(event.x, event.y) }
            if (tappedLevel >= 0) {
                currentLevel = tappedLevel
                onLevelSelected?.invoke(tappedLevel)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
```

**Critical rules for custom View drawing:**
- Never allocate `Paint`, `Path`, or `RectF` objects inside `onDraw()` — allocate them as class fields.
- Call `invalidate()` whenever state changes to trigger a redraw.
- Pre-compute all segment `RectF` objects in `onSizeChanged()`, not in `onDraw()`.
- Return `true` from `onTouchEvent` when you consume the event.
- Use `RectF.contains(x, y)` for hit-testing a tap coordinate against a segment rectangle.

---

### 1.6 Third-Party Library Option

**`rayzone107/SegmentedProgressBar`** is the closest existing library for this use case:

```xml
<com.example.segmentedprogressbar.SegmentedProgressBar
    android:id="@+id/spb"
    android:layout_width="match_parent"
    android:layout_height="8dp"
    app:divisions="6"
    app:progressBarColor="#6200EE"
    app:progressBarBackgroundColor="#E0E0E0"
    app:dividerWidth="3dp"
    app:isDividerEnabled="true"
    app:cornerRadius="4dp"/>
```

```kotlin
// Enable segments 0–currentLevel
val enabledList = (0..currentLevel).toList()
spb.setEnabledDivisions(enabledList)
```

**Limitation:** This library does not expose tap/click handlers on individual segments. You must layer a transparent `RecyclerView` or individual `View` elements on top to capture taps. For a 0–5 tap-to-jump requirement, the custom `View` or Compose approach is more direct.

---

## 2. Habit Progression Psychology

### 2.1 BJ Fogg — Tiny Habits and the Behavior Model

**The Fogg Behavior Model: B = MAP**

- **B** (Behavior) happens only when **M** (Motivation), **A** (Ability), and **P** (Prompt) converge simultaneously.
- Lowering ability requirements (making a habit easier) compensates for lower motivation — this is the theoretical basis for a tiered difficulty system.

**Two strategies for making habits achievable:**

| Strategy | Description | App Mapping |
|---|---|---|
| Starter Step | Isolate the very first action (e.g., "put on shoes" for a run) | Level 1 description |
| Scaling Back | Do a smaller portion of the behavior | Level 1–2 descriptions |

**Progression principle:** Fogg's framework calls for a "Repeat, refine, and upgrade" cycle. Habits naturally expand as they solidify. The app's auto-promotion concept maps directly to this: once a user consistently completes a level, the system upgrades them automatically.

**Celebration timing:** Fogg emphasizes celebrating *immediately* after completing the behavior — before the next action. This wires the neural pathway more strongly. In-app: show a positive animation/sound when the user taps "Did it," before the level check logic runs.

---

### 2.2 Stephen Guise — Elastic Habits (Three-Tier Model)

Elastic Habits provides the clearest existing framework for a graduated difficulty system:

| Tier | Label | Duration | When to Use | App Level Mapping |
|---|---|---|---|---|
| 1 (Minimum) | Mini | 5–10 min | Bad days, safety net | Levels 0–1 |
| 2 (Target) | Plus | 20–45 min | Most days | Levels 2–3 |
| 3 (Ambitious) | Elite | 60+ min | High-energy days | Levels 4–5 |

**Key ratios between levels:**
- Plus should be roughly 3–20× harder than Mini.
- Elite should be roughly 2–4× harder than Plus.

**Critical insight for the app:** All three tiers count equally toward consistency. The system's value is preventing the all-or-nothing trap — a user who does Mini on a hard day maintains their streak. For the app's auto-promotion logic, this argues for promoting based on "what level did they consistently complete" rather than simple binary completion.

**Recalibration signal:** If the user consistently operates at Level 0–1, their Level 2 description may be set too high and should prompt a revision. The app could suggest this after N consecutive low-level completions.

---

### 2.3 Streak Research and Auto-Promotion Thresholds

Research on streak-based systems (Duolingo, Headspace, etc.) provides empirical data:

- **7-day threshold**: The most critical milestone. Once users reach 7 consecutive days, loss-aversion psychology activates significantly. Users become 2.3× more likely to engage daily (Duolingo internal data).
- **30-day milestone**: A common secondary milestone where apps celebrate and potentially unlock new content/levels.
- **Apps combining streaks + milestones** show 40–60% higher Daily Active Users vs. single-feature implementations (Forrester, 2024).

**Recommended auto-promotion thresholds for the app:**

| Streak Length | Action |
|---|---|
| 7 consecutive days at current level | Offer promotion to next level |
| 14 consecutive days | Auto-promote (with user notification) |
| 3 consecutive days below current level | Suggest adjusting level descriptions |

These are starting recommendations — the thresholds should be configurable per habit or globally in settings.

**Loss-aversion consideration:** Users feel losses ~2× more intensely than equivalent gains. Auto-demotion (lowering level on missed days) should be handled carefully or avoided entirely. The issue's proposal to auto-promote but not auto-demote is psychologically sound.

---

## 3. Android Room Database — JSON Array Storage

### 3.1 Schema Change: Adding a JSON Column

The proposed change adds a `levelDescriptions` column of type `TEXT` (storing a JSON array of 6 strings) to the existing `habits` (or equivalent) table.

**Entity definition:**

```kotlin
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val dedicationLevel: Int = 0,          // 0-5, current active level
    @ColumnInfo(defaultValue = "[]")
    val levelDescriptions: List<String> = emptyList()  // JSON array via TypeConverter
)
```

---

### 3.2 TypeConverter for `List<String>`

```kotlin
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}
```

**Alternative: Using `kotlinx.serialization` (no Gson dependency):**

```kotlin
import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        Json.encodeToString(value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else Json.decodeFromString(value)
}
```

`kotlinx.serialization` is preferred if the project already uses it (avoids adding the Gson transitive dependency).

---

### 3.3 Registering the TypeConverter

```kotlin
@Database(
    entities = [Habit::class],
    version = 2,   // Bumped from 1 → 2 for this migration
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
}
```

`@TypeConverters` at the `@Database` level applies the converter globally to all entities, DAOs, and queries in that database. Alternatively, scope it to a specific `@Entity` or `@Dao` for finer control.

---

### 3.4 Database Migration

**Option A — Automatic Migration (preferred if only adding a nullable/default column):**

Room 2.4+ can auto-migrate simple column additions when the new column has a default value defined in the entity's `@ColumnInfo`:

```kotlin
@ColumnInfo(defaultValue = "[]")
val levelDescriptions: List<String> = emptyList()
```

With `AutoMigration(from = 1, to = 2)` in the `@Database` annotation, Room generates the required `ALTER TABLE` SQL automatically. This works because:
- The column is a `TEXT` type (stored as JSON string).
- It has a default value (`"[]"`) for existing rows.

**Option B — Manual Migration (fallback):**

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE habits ADD COLUMN levelDescriptions TEXT NOT NULL DEFAULT '[]'"
        )
    }
}

Room.databaseBuilder(context, AppDatabase::class.java, "habits.db")
    .addMigrations(MIGRATION_1_2)
    .build()
```

**Recommendation:** Use AutoMigration (Option A) first. If Room raises a compile-time error (which happens for complex changes like renames or type shifts), fall back to the manual migration.

**Schema export requirement for AutoMigration:**

```kotlin
// In build.gradle.kts (app module)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
// or with the Room Gradle Plugin:
room {
    schemaDirectory("$projectDir/schemas")
}
```

---

### 3.5 Also Adding `dedicationLevel` Column

The `dedicationLevel: Int` column (current active level 0–5) is a simple integer addition and auto-migrates trivially:

```kotlin
@ColumnInfo(defaultValue = "0")
val dedicationLevel: Int = 0
```

Both columns can be added in a single `AutoMigration(from = 1, to = 2)`.

---

### 3.6 DAO Queries

```kotlin
@Dao
interface HabitDao {

    @Query("SELECT * FROM habits WHERE id = :id")
    fun getHabitById(id: Long): Flow<Habit?>

    @Query("UPDATE habits SET dedicationLevel = :level WHERE id = :id")
    suspend fun updateDedicationLevel(id: Long, level: Int)

    @Query("UPDATE habits SET levelDescriptions = :descriptions WHERE id = :id")
    suspend fun updateLevelDescriptions(id: Long, descriptions: List<String>)

    // Auto-promotion: find habits where streak >= threshold and level < 5
    @Query("""
        SELECT * FROM habits 
        WHERE currentStreak >= :streakThreshold AND dedicationLevel < 5
    """)
    fun getHabitsEligibleForPromotion(streakThreshold: Int): Flow<List<Habit>>
}
```

---

## 4. Habit Ladder Theory — Behavioral Design

### 4.1 Framework Synthesis

Combining Fogg (B=MAP) and Guise (Elastic Habits) produces a practical 6-level description template:

| Level | Label (Suggested) | Description Pattern | Fogg Analogy |
|---|---|---|---|
| 0 | Acknowledge | "Simply think about [habit title] for 1 minute" | Starter step |
| 1 | Begin | "Do the smallest possible version of [habit title]" | Scaled-back |
| 2 | Practice | "Complete a short session of [habit title]" | Regular |
| 3 | Commit | "Complete a full session of [habit title]" | Full |
| 4 | Excel | "Do [habit title] with extra effort or duration" | Enhanced |
| 5 | Master | "Do [habit title] at peak performance" | Elite |

For AI-generated descriptions from habit title, this table provides the prompt structure. Example prompt:

```
Given the habit titled "{habitTitle}", generate 6 progressive descriptions 
for dedication levels 0-5. Each description should be a single sentence 
(max 12 words). Level 0 is the absolute minimum (takes < 2 minutes), 
Level 5 is the most ambitious version. Return a JSON array of 6 strings.
```

---

### 4.2 Progression Design Principles

**From BJ Fogg's Tiny Habits:**
- Make the lowest level so easy it feels "embarrassingly small" — this ensures users maintain streaks on bad days.
- Celebrate immediately after completion (before incrementing UI state) — reinforces the neural habit loop.
- Do not require high motivation for low levels; the system should work when motivation = minimum.

**From Elastic Habits:**
- The Mini level (0–1) is a safety net, not a failure state — it counts fully.
- Users naturally graduate from lower levels over time without external pressure; don't auto-demote.
- If a user is stuck at Level 0 for weeks, the Level 1 description is too hard — the app should surface a "revisit your levels" prompt.

**From streak research:**
- 7-day consistency is the inflection point for habit solidification.
- Frame auto-promotion as an achievement ("You've mastered Level 2!") not a notification ("Level increased").
- Allow manual level adjustment — users know their context better than streaks do.

---

## 5. Android Notification — Single Action ("Did it")

### 5.1 Single Action vs. Multiple Actions

**Current state (two buttons):** Having both "Completed Easy" and "Completed Full" in a notification requires users to make a decision under cognitive load (while being interrupted by the notification). This violates the principle that "the more actions you include, the more cognitive complexity you create."

**Proposed state (one button — "Did it"):** A single action is the minimal viable interaction. The dedication level is managed proactively in-app, not reactively per notification.

Android documentation guidance:
- "Limit yourself to the fewest number of actions possible by only including the most imminently important and meaningful ones."
- "Action buttons must not duplicate the action performed when the user taps the notification."
- A notification can have up to 3 actions, but 1 is strongly preferred for habit reminders.

---

### 5.2 Implementation — Single Action Notification

```kotlin
class HabitNotificationBuilder(private val context: Context) {

    fun build(
        habitId: Long,
        habitTitle: String,
        notificationId: Int
    ): Notification {

        // PendingIntent for tapping the notification body → opens app to habit detail
        val openIntent = Intent(context, HabitDetailActivity::class.java).apply {
            putExtra("habitId", habitId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, habitId.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent for "Did it" action → fires BroadcastReceiver
        val didItIntent = Intent(context, HabitCompletionReceiver::class.java).apply {
            action = HabitCompletionReceiver.ACTION_DID_IT
            putExtra(HabitCompletionReceiver.EXTRA_HABIT_ID, habitId)
            putExtra(HabitCompletionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val didItPendingIntent = PendingIntent.getBroadcast(
            context, habitId.toInt(), didItIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_habit_check)
            .setContentTitle(habitTitle)
            .setContentText("Tap 'Did it' to log completion at your current level")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            // Single action — no cognitive choice required
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.action_did_it),   // "Did it"
                didItPendingIntent
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "habit_reminders"
    }
}
```

---

### 5.3 BroadcastReceiver — Dismiss and Record Completion

```kotlin
class HabitCompletionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DID_IT) return

        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (habitId < 0) return

        // Dismiss the notification
        NotificationManagerCompat.from(context).cancel(notificationId)

        // Record completion at current dedication level (no level choice needed)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val repository = HabitRepository.getInstance(context)
            repository.recordCompletion(habitId)
            // Auto-promotion check happens inside recordCompletion()
        }
    }

    companion object {
        const val ACTION_DID_IT = "com.yourapp.ACTION_DID_IT"
        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
```

**Key points:**
- `setAutoCancel(true)` on the builder dismisses the notification when the user taps the *body*, but NOT when they tap an action button — you must call `NotificationManagerCompat.cancel(notificationId)` explicitly inside the receiver.
- Pass `notificationId` as an extra so the receiver can dismiss the correct notification.
- Use `PendingIntent.FLAG_IMMUTABLE` (required on Android 12+, API 31+).
- `BroadcastReceiver.onReceive()` runs on the main thread with a 10-second deadline — start a coroutine for the database write, but the notification cancel is safe to call synchronously.

---

### 5.4 Notification Channel Configuration

```kotlin
fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            HabitNotificationBuilder.CHANNEL_ID,
            "Habit Reminders",
            NotificationManager.IMPORTANCE_DEFAULT   // Makes sound, shows in status bar
        ).apply {
            description = "Daily reminders for your habits"
            enableVibration(true)
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
```

**Importance level guidance:**
- `IMPORTANCE_HIGH` (shows as heads-up): Only for time-critical habits (e.g., medication).
- `IMPORTANCE_DEFAULT` (makes sound): Appropriate for most habit reminders.
- `IMPORTANCE_LOW` (silent): Optional/low-priority habit nudges.

---

### 5.5 System-Generated Action Buttons (Android 10+)

On Android 10+ (API 29+), the system may auto-generate contextual reply actions based on notification content. If the habit title text could trigger unwanted auto-suggestions, opt out:

```kotlin
NotificationCompat.Builder(context, CHANNEL_ID)
    .setAllowSystemGeneratedContextualActions(false)
    // ... other builder calls
```

---

## 6. Recommendations Summary

### Architecture Decisions

| Decision | Recommendation | Rationale |
|---|---|---|
| UI component for level selection | Jetpack Compose `Slider(steps=4, valueRange=0f..5f)` | Built-in snap, no custom drawing, accessibility handled |
| Alternative if Compose not used | Custom `View` with `RectF.contains()` in `onTouchEvent` | Correct approach for View system |
| JSON storage | `TypeConverter` with `kotlinx.serialization` or Gson | Standard pattern, avoids schema complexity |
| DB migration | `AutoMigration(from = N, to = N+1)` with `@ColumnInfo(defaultValue="[]")` | Least error-prone for simple column additions |
| Notification | Single "Did it" action + `BroadcastReceiver` cancel | Reduces cognitive load, follows Google guidelines |
| Auto-promotion threshold | Promote after 7 consecutive completions at current level | Backed by streak research inflection points |
| Auto-demotion | Do not auto-demote | Psychologically sound per Fogg/Guise frameworks |
| AI description generation | Prompt with 6-level ladder template | Ensures descriptions are appropriately graduated |

### Behavioral Design Recommendations

1. **Level 0 must be achievable on the worst possible day** — "Think about [habit]" or "Do 1 rep." If it's not that easy, users lose streaks and disengage.
2. **Celebrate immediately after "Did it"** — animate the progress bar fill before showing streak count, mirroring Fogg's celebration-before-reward-check principle.
3. **Frame auto-promotion as an achievement** — show a full-screen moment or bottom sheet: "You've leveled up to Dedication 3!" not a silent increment.
4. **Allow manual level override always** — the segmented bar should be tappable in the habit detail view at any time, not just during auto-promotion. Users may want to push themselves above the auto-promoted level.
5. **Show current level description in the notification** — include the Level N description text in the notification body so users are reminded what they committed to, without opening the app.

---

## Sources

- [GitHub: rayzone107/SegmentedProgressBar](https://github.com/rayzone107/SegmentedProgressBar)
- [Medium: Creating a Custom Segmented Progress Bar in Android](https://medium.com/@basaktuysuz1/creating-a-custom-segmented-progress-bar-in-android-082e7cdc4b5a)
- [Medium: Building a Segmented Progress Bar in Android (betclic-tech)](https://medium.com/betclic-tech/building-a-segmented-progress-bar-in-android-e3f198db393d)
- [Medium: Building Custom Segmented Controllers in Jetpack Compose](https://medium.com/@priteshnikam/building-custom-segmented-controllers-in-jetpack-compose-67bd663f00b5)
- [Medium: Production-Ready Custom Slider in Jetpack Compose](https://medium.com/pickme-engineering-blog/building-a-production-ready-custom-slider-in-jetpack-compose-a-deep-dive-into-haptic-feedback-521199df553c)
- [Android Developers: Slider (Jetpack Compose)](https://developer.android.com/develop/ui/compose/components/slider)
- [Android Developers: Mastering Touch Events for Custom Views (MoldStud)](https://moldstud.com/articles/p-master-touch-events-for-custom-views-in-android)
- [Android Developers: Handling touch events in a ViewGroup](https://developer.android.com/develop/ui/views/touch-and-input/gestures/viewgroup)
- [Medium: Android Custom Views — Handling Touch Events](https://muhammetkudur.medium.com/android-custom-views-2-handling-touch-events-cc46b3cf17c2)
- [Android Developers: Referencing complex data using Room](https://developer.android.com/training/data-storage/room/referencing-data)
- [Android Developers: Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Medium: How to save List of Data in Table Column using Type Converter & Gson](https://ngima.medium.com/how-to-save-list-of-data-in-table-column-in-room-using-type-converter-gson-691aa780ab19)
- [DEV.to: Room Database Auto Migration](https://dev.to/slowburn404/room-database-auto-migration-15cb)
- [BJ Fogg: Tiny Habits — The Small Changes That Change Everything](https://tinyhabits.com/)
- [Shortform: Tiny Habits Summary — BJ Fogg](https://www.shortform.com/summary/tiny-habits-summary-bj-fogg)
- [Workbrighter: The Brighter Guide to Tiny Habits](https://workbrighter.co/tiny-habits/)
- [HabitDex: Elastic Habits — How It Works](https://habitdex.com/methods/elastic-habits)
- [minihabits.com: How to Choose Your Habit Intensity (Elastic Habits)](https://minihabits.com/three-ways-to-choose-your-habit-intensity-every-day-with-elastic-habits/)
- [Plotline: Streaks and Milestones for Gamification in Mobile Apps](https://www.plotline.so/blog/streaks-for-gamification-in-mobile-apps/)
- [Yu-kai Chou: Master the Art of Streak Design](https://yukaichou.com/gamification-study/master-the-art-of-streak-design-for-short-term-engagement-and-long-term-success/)
- [Android Developers: Create a notification](https://developer.android.com/develop/ui/views/notifications/build-notification)
- [Android Developers: Notifications design guide](https://developer.android.com/design/ui/mobile/guides/home-screen/notifications)
- [Naavik: New Horizons in Habit-Building Gamification](https://naavik.co/deep-dives/deep-dives-new-horizons-in-gamification/)
- [ScienceDirect: Leveraging cognitive neuroscience for making and breaking habits](https://www.sciencedirect.com/science/article/pii/S1364661324002663)
