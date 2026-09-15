package net.interstellarai.unreminder.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.LocalSize
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.action.mutableActionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import net.interstellarai.unreminder.MainActivity
import net.interstellarai.unreminder.domain.model.VariantTreatment
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.notification.SpriteResolver
import javax.inject.Inject

/**
 * The widget's ambient progress line: whether today already counts and the same
 * days-with-something-done total the Now page shows.
 */
data class DayProgress(val completedToday: Boolean, val daysWithAnyCompletion: Int)

/**
 * Home-screen widget: one habit, its peeked variant and sprite, and a "did it" button — the
 * card itself opens that variant's view — or, with no active habit to offer, a prompt to add
 * one that opens the Now page — plus a one-line day progress indicator,
 * drawn in the [WidgetLayout] its variant derives. The variant is the
 * card's headline and the habit name its small label above it. Below its default
 * size it collapses to a single strip. It only renders what [WidgetRefresher] last stored;
 * every recompute goes through the refresher.
 */
class DoableHabitWidget : GlanceAppWidget() {

    // Two forms only, so two buckets: the launcher composes the largest one that fits and
    // LocalSize reports that bucket, never the true size.
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(STRIP, FULL))

    companion object {
        /** minResizeWidth/minResizeHeight in doable_habit_widget_info.xml. */
        internal val STRIP = DpSize(110.dp, 40.dp)

        // Deliberately under the declared 180×110 minWidth/minHeight: launchers report a
        // default placement a few dp smaller than declared, and a bucket that only just fits
        // would hand an unresized widget the strip.
        internal val FULL = DpSize(160.dp, 96.dp)

        private val HABIT_ID = longPreferencesKey("habit_id")
        private val HABIT_NAME = stringPreferencesKey("habit_name")
        private val EMOJI = stringPreferencesKey("emoji")
        private val TEXT = stringPreferencesKey("text")
        private val VARIATION_ID = longPreferencesKey("variation_id")
        // The tag, not the drawable id: stored state outlives an app update, and resource
        // ids do not, whereas an unknown tag still resolves to something to show.
        private val SPRITE_TAG = stringPreferencesKey("sprite_tag")
        private val COMPLETED_TODAY = booleanPreferencesKey("completed_today")
        private val DAYS_WITH_ANY_COMPLETION = intPreferencesKey("days_with_any_completion")

        // One write for both so the habit can never land without the progress that goes
        // with it — a completion from the widget must flip the indicator in the same refresh.
        internal fun store(prefs: MutablePreferences, habit: DoableHabit?, progress: DayProgress) {
            if (habit == null) {
                prefs.remove(HABIT_ID)
                prefs.remove(HABIT_NAME)
                prefs.remove(EMOJI)
                prefs.remove(TEXT)
                prefs.remove(VARIATION_ID)
                prefs.remove(SPRITE_TAG)
            } else {
                prefs[HABIT_ID] = habit.id
                prefs[HABIT_NAME] = habit.name
                prefs[EMOJI] = habit.emoji
                if (habit.text != null) prefs[TEXT] = habit.text else prefs.remove(TEXT)
                if (habit.variationId != null) prefs[VARIATION_ID] = habit.variationId else prefs.remove(VARIATION_ID)
                if (habit.spriteTag != null) prefs[SPRITE_TAG] = habit.spriteTag else prefs.remove(SPRITE_TAG)
            }
            prefs[COMPLETED_TODAY] = progress.completedToday
            prefs[DAYS_WITH_ANY_COMPLETION] = progress.daysWithAnyCompletion
        }

        /** The shown variant's look; with no habit the add-a-habit prompt keeps the original one. */
        internal fun layoutFor(habit: DoableHabit?): WidgetLayout =
            habit?.let { WidgetLayout.forSeed(VariantTreatment.seed(it.variationId, it.id)) } ?: WidgetLayout.SPRITE_LEFT

        internal fun stored(prefs: Preferences): DoableHabit? {
            val id = prefs[HABIT_ID] ?: return null
            val name = prefs[HABIT_NAME] ?: return null
            return DoableHabit(
                id = id,
                name = name,
                emoji = prefs[EMOJI].orEmpty(),
                text = prefs[TEXT],
                variationId = prefs[VARIATION_ID],
                spriteTag = prefs[SPRITE_TAG],
            )
        }

        /** Null until the first refresh after placement (or after the update that added it). */
        internal fun storedDayProgress(prefs: Preferences): DayProgress? {
            val completedToday = prefs[COMPLETED_TODAY] ?: return null
            val days = prefs[DAYS_WITH_ANY_COMPLETION] ?: return null
            return DayProgress(completedToday, days)
        }

        // Done reads as earned and not-yet as an open door: there are no streaks, so nothing
        // here may hint at loss. "so far" keeps the count reading as the Now page's running
        // total of days with something done, not as a run that could end.
        internal fun dayProgressLabel(progress: DayProgress): String {
            val today = if (progress.completedToday) "\u2713 today counts" else "today's still open"
            val days = progress.daysWithAnyCompletion
            return "$today \u00b7 $days ${if (days == 1) "day" else "days"} so far"
        }

        // The strip has no room for the progress line, so the earned-day half of it moves
        // onto the title; the day count waits for the full size.
        internal fun stripTitle(habit: DoableHabit, progress: DayProgress?): String {
            val prefix = if (progress?.completedToday == true) "\u2713 " else ""
            return "$prefix${habit.emoji} ${habit.name}"
        }

        // The variant is the headline wherever there is one; without a variant the habit name
        // takes its place.
        internal fun headline(habit: DoableHabit): String = habit.text ?: "${habit.emoji} ${habit.name}"

        // The small line that keeps the habit identifiable above its headline; with no variant
        // the name is already the headline, so there is nothing to label.
        internal fun habitLabel(habit: DoableHabit): String? =
            if (habit.text == null) null else "${habit.emoji} ${habit.name}"

        /** With no habit to show, tapping the card opens the app on the Now menu. */
        internal fun openNowIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(NotificationHelper.EXTRA_OPEN_NOW, true)
            }

        /**
         * Tapping anywhere but "did it" opens the variant view for the shown habit. PendingIntent
         * matching ignores extras, so two placed widgets showing different habits would collapse
         * to one without the per-habit data URI; MainActivity reads only the extras.
         */
        internal fun openVariantIntent(context: Context, habit: DoableHabit): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("unreminder://variant/${habit.id}/${habit.variationId ?: -1L}")
                putExtra(NotificationHelper.EXTRA_OPEN_VARIANT, true)
                putExtra(NotificationHelper.EXTRA_HABIT_ID, habit.id)
                putExtra(NotificationHelper.EXTRA_VARIATION_ID, habit.variationId ?: -1L)
            }

        /** What the card opens: the shown habit's variant, or the Now menu when there is none. */
        internal fun cardIntent(context: Context, habit: DoableHabit?): Intent =
            if (habit == null) openNowIntent(context) else openVariantIntent(context, habit)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val spriteResolver = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).spriteResolver()
        provideContent {
            val state = currentState<Preferences>()
            val habit = stored(state)
            WidgetContent(layoutFor(habit), habit, storedDayProgress(state), spriteResolver)
        }
    }
}

@Composable
private fun WidgetContent(
    layout: WidgetLayout,
    habit: DoableHabit?,
    progress: DayProgress?,
    spriteResolver: SpriteResolver,
) {
    val context = LocalContext.current
    val open = DoableHabitWidget.cardIntent(context, habit)
    val strip = LocalSize.current.height < DoableHabitWidget.FULL.height
    val card = GlanceModifier
        .fillMaxSize()
        .background(layout.palette.surface)
        .cornerRadius(16.dp)
        .clickable(actionStartActivity(open))
    Box(
        modifier = if (strip) card.padding(horizontal = 10.dp, vertical = 5.dp) else card.padding(fullPadding(layout)),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            habit == null -> NoActiveHabits(layout.palette.ink, progress, strip)
            strip -> Strip(layout, habit, progress)
            else -> Full(layout, habit, progress, spriteResolver)
        }
    }
}

// Below the default size every layout collapses to one row — title, "did it" — and keeps
// only its palette and title face. At 110×40dp the button, a sprite, a variant line and the
// progress line cannot share the card with a readable name, and the name and the tap are
// what have to survive.
@Composable
private fun Strip(layout: WidgetLayout, habit: DoableHabit, progress: DayProgress?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = DoableHabitWidget.stripTitle(habit, progress),
            style = TextStyle(
                color = layout.palette.ink,
                fontSize = 13.sp,
                fontWeight = layout.titleWeight,
                fontFamily = layout.titleFamily,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(6.dp))
        DidIt(habit, layout.palette, compact = true, horizontalPadding = 8.dp, verticalPadding = 3.dp)
    }
}

// The denser the composition, the less it can give to the card's edge.
private fun fullPadding(layout: WidgetLayout): Dp = when (layout) {
    WidgetLayout.SPRITE_LEFT, WidgetLayout.SPRITE_RIGHT -> 16.dp
    WidgetLayout.SPRITE_LARGE, WidgetLayout.TYPOGRAPHIC -> 12.dp
    WidgetLayout.COMPACT -> 10.dp
}

@Composable
private fun Full(layout: WidgetLayout, habit: DoableHabit, progress: DayProgress?, spriteResolver: SpriteResolver) {
    when (layout) {
        WidgetLayout.SPRITE_LEFT -> SpriteLeft(habit, progress, layout.palette, spriteResolver)
        WidgetLayout.SPRITE_RIGHT -> SpriteRight(habit, progress, layout.palette, spriteResolver)
        WidgetLayout.SPRITE_LARGE -> SpriteLarge(habit, progress, layout.palette, spriteResolver)
        WidgetLayout.TYPOGRAPHIC -> Typographic(habit, progress, layout.palette)
        WidgetLayout.COMPACT -> Compact(habit, progress, layout.palette, spriteResolver)
    }
}

// Every full composition is budgeted for FULL, which is all LocalSize ever reports for it;
// larger placements get slack, content stays start-aligned.

@Composable
private fun SpriteLeft(habit: DoableHabit, progress: DayProgress?, palette: WidgetPalette, spriteResolver: SpriteResolver) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sprite(habit, spriteResolver, GlanceModifier.size(width = 88.dp, height = 74.dp).cornerRadius(8.dp))
            Spacer(GlanceModifier.width(12.dp))
            Column {
                HabitLabel(habit, palette.ink, 11.sp)
                Headline(habit, TextStyle(color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                Spacer(GlanceModifier.height(8.dp))
                DidIt(habit, palette, compact = false)
            }
        }
        if (progress != null) {
            Spacer(GlanceModifier.height(6.dp))
            DayProgressLine(progress, palette.ink, 11.sp)
        }
    }
}

@Composable
private fun SpriteRight(habit: DoableHabit, progress: DayProgress?, palette: WidgetPalette, spriteResolver: SpriteResolver) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                HabitLabel(habit, palette.ink, 11.sp)
                Headline(habit, TextStyle(color = palette.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 2)
                Spacer(GlanceModifier.height(8.dp))
                DidIt(habit, palette, compact = false)
            }
            Spacer(GlanceModifier.width(12.dp))
            Sprite(habit, spriteResolver, GlanceModifier.size(72.dp).cornerRadius(36.dp))
        }
        if (progress != null) {
            Spacer(GlanceModifier.height(6.dp))
            DayProgressLine(progress, palette.ink, 11.sp)
        }
    }
}

// Image-dominant: the sprite fills the left half and the text is deliberately the small part,
// so the headline stays at 13sp.
@Composable
private fun SpriteLarge(habit: DoableHabit, progress: DayProgress?, palette: WidgetPalette, spriteResolver: SpriteResolver) {
    Row(modifier = GlanceModifier.fillMaxSize()) {
        Sprite(habit, spriteResolver, GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(12.dp))
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            HabitLabel(habit, palette.ink, 10.sp)
            Headline(habit, TextStyle(color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 2)
            Spacer(GlanceModifier.height(6.dp))
            DidIt(habit, palette, compact = false)
            if (progress != null) {
                Spacer(GlanceModifier.height(6.dp))
                DayProgressLine(progress, palette.ink, 10.sp)
            }
        }
    }
}

// No sprite: the headline carries the card in the app's serif display face.
@Composable
private fun Typographic(habit: DoableHabit, progress: DayProgress?, palette: WidgetPalette) {
    Column {
        HabitLabel(habit, palette.ink, 11.sp)
        Headline(
            habit,
            TextStyle(color = palette.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
            maxLines = 2,
        )
        Spacer(GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DidIt(habit, palette, compact = true)
            if (progress != null) {
                Spacer(GlanceModifier.width(10.dp))
                DayProgressLine(progress, palette.ink, 11.sp, GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun Compact(habit: DoableHabit, progress: DayProgress?, palette: WidgetPalette, spriteResolver: SpriteResolver) {
    val label = DoableHabitWidget.habitLabel(habit)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sprite(habit, spriteResolver, GlanceModifier.size(28.dp).cornerRadius(6.dp))
            Spacer(GlanceModifier.width(8.dp))
            // The two text slots sit in different containers, so the no-variant case, where the
            // name is the headline, has to move it up into the row itself.
            if (label != null) {
                HabitLabel(habit, palette.ink, 11.sp, GlanceModifier.defaultWeight())
            } else {
                Headline(
                    habit,
                    TextStyle(color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            DidIt(habit, palette, compact = true)
        }
        if (label != null) {
            Spacer(GlanceModifier.height(4.dp))
            Headline(habit, TextStyle(color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 2)
        }
        if (progress != null) {
            Spacer(GlanceModifier.height(4.dp))
            DayProgressLine(progress, palette.ink, 10.sp)
        }
    }
}

// Shown untinted: the tiles are the one saturated thing on the calm palette.
@Composable
private fun Sprite(habit: DoableHabit, spriteResolver: SpriteResolver, modifier: GlanceModifier) {
    Image(
        provider = ImageProvider(spriteResolver.resolve(habit.spriteTag, rotationSeed = habit.id)),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

// Every full layout is budgeted for FULL, so the headline is capped at two lines rather than
// letting a long variant push the button or the progress line off the card.
@Composable
private fun Headline(habit: DoableHabit, style: TextStyle, maxLines: Int, modifier: GlanceModifier = GlanceModifier) {
    Text(text = DoableHabitWidget.headline(habit), style = style, maxLines = maxLines, modifier = modifier)
}

@Composable
private fun HabitLabel(habit: DoableHabit, ink: ColorProvider, size: TextUnit, modifier: GlanceModifier = GlanceModifier) {
    val label = DoableHabitWidget.habitLabel(habit) ?: return
    Text(text = label, style = TextStyle(color = ink, fontSize = size), maxLines = 1, modifier = modifier)
}

// The card inverted: label contrast equals the card's text contrast, and the button can never
// vanish into an accent-coloured surface.
@Composable
private fun DidIt(
    habit: DoableHabit,
    palette: WidgetPalette,
    compact: Boolean,
    horizontalPadding: Dp = if (compact) 12.dp else 16.dp,
    verticalPadding: Dp = if (compact) 4.dp else 8.dp,
) {
    Button(
        text = "did it",
        onClick = actionRunCallback<MarkDoneAction>(MarkDoneAction.parameters(habit)),
        colors = ButtonDefaults.buttonColors(backgroundColor = palette.ink, contentColor = palette.surface),
        style = TextStyle(fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.Medium),
        modifier = GlanceModifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
    )
}

// One small line under whatever else is showing, so it costs the sprite and button nothing.
@Composable
private fun DayProgressLine(progress: DayProgress, ink: ColorProvider, size: TextUnit, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text = DoableHabitWidget.dayProgressLabel(progress),
        style = TextStyle(color = ink, fontSize = size),
        maxLines = 1,
        modifier = modifier,
    )
}

// The whole widget already opens the Now page, whose empty state leads to the habit editor.
@Composable
private fun NoActiveHabits(ink: ColorProvider, progress: DayProgress?, strip: Boolean) {
    Column {
        Text(
            text = "no active habits \u00b7 tap to add one",
            style = TextStyle(color = ink, fontSize = if (strip) 13.sp else 16.sp),
            maxLines = if (strip) 1 else Int.MAX_VALUE,
        )
        if (progress != null && !strip) {
            Spacer(GlanceModifier.height(6.dp))
            DayProgressLine(progress, ink, 11.sp)
        }
    }
}

@AndroidEntryPoint
class DoableHabitWidgetReceiver : GlanceAppWidgetReceiver() {

    @Inject
    lateinit var widgetRefresher: WidgetRefresher

    override val glanceAppWidget: GlanceAppWidget = DoableHabitWidget()

    // Placement and the 30-minute tick both land here; a bare re-render would only repeat
    // the stored pick, so the tick has to recompute.
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        widgetRefresher.refresh()
    }
}

class MarkDoneAction : ActionCallback {

    companion object {
        val HABIT_ID = ActionParameters.Key<Long>("habit_id")
        val VARIATION_ID = ActionParameters.Key<Long>("variation_id")
        private const val TAG = "MarkDoneAction"

        fun parameters(habit: DoableHabit): ActionParameters =
            mutableActionParametersOf(HABIT_ID to habit.id).apply {
                habit.variationId?.let { this[VARIATION_ID] = it }
            }
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val habitId = parameters[HABIT_ID]
        if (habitId != null) {
            try {
                entryPoint.completionRecorder().complete(habitId, parameters[VARIATION_ID], PullCompletionRecorder.SOURCE_WIDGET)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to record widget completion for habit $habitId", e)
                Sentry.captureException(e) { scope ->
                    scope.setTag("component", "widget-done")
                    scope.setTag("habit_id", habitId.toString())
                }
            }
        }
        // Even a failed write must not leave the tapped habit lingering as if it were still on.
        try {
            entryPoint.widgetRefresher().refreshNow()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to refresh the widget after marking habit $habitId done", e)
            Sentry.captureException(e) { scope ->
                scope.setTag("component", "widget-done-refresh")
                habitId?.let { scope.setTag("habit_id", it.toString()) }
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun completionRecorder(): PullCompletionRecorder
    fun widgetRefresher(): WidgetRefresher
    fun spriteResolver(): SpriteResolver
}
