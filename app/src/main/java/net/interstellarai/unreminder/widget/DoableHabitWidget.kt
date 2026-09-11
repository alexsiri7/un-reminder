package net.interstellarai.unreminder.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.action.mutableActionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
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
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.notification.SpriteResolver
import javax.inject.Inject

/**
 * Home-screen widget: one doable habit, its peeked variant and sprite, and a "did it" button,
 * or a resting state. It only renders what [WidgetRefresher] last stored; every recompute
 * goes through the refresher.
 */
class DoableHabitWidget : GlanceAppWidget() {

    companion object {
        private val HABIT_ID = longPreferencesKey("habit_id")
        private val HABIT_NAME = stringPreferencesKey("habit_name")
        private val EMOJI = stringPreferencesKey("emoji")
        private val TEXT = stringPreferencesKey("text")
        private val VARIATION_ID = longPreferencesKey("variation_id")
        // The tag, not the drawable id: stored state outlives an app update, and resource
        // ids do not, whereas an unknown tag still resolves to something to show.
        private val SPRITE_TAG = stringPreferencesKey("sprite_tag")

        fun store(prefs: MutablePreferences, habit: DoableHabit?) {
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
        }

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

        /** Tapping anywhere but "did it" opens the app on the Now menu. */
        internal fun openNowIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(NotificationHelper.EXTRA_OPEN_NOW, true)
            }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val spriteResolver = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).spriteResolver()
        provideContent {
            WidgetContent(stored(currentState()), spriteResolver)
        }
    }
}

@Composable
private fun WidgetContent(habit: DoableHabit?, spriteResolver: SpriteResolver) {
    val openNow = DoableHabitWidget.openNowIntent(LocalContext.current)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity(openNow))
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (habit == null) Resting() else Suggestion(habit, spriteResolver)
    }
}

// The sprite is the widget's dominant element and is shown untinted: the tiles are the one
// saturated thing on the calm palette. Variants are written for notification bodies, so the
// text is capped at two lines rather than letting the layout grow.
@Composable
private fun Suggestion(habit: DoableHabit, spriteResolver: SpriteResolver) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(spriteResolver.resolve(habit.spriteTag, rotationSeed = habit.id)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier
                .size(width = 88.dp, height = 74.dp)
                .cornerRadius(8.dp),
        )
        Spacer(GlanceModifier.width(12.dp))
        Column {
            Text(
                text = "${habit.emoji} ${habit.name}",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            if (habit.text != null) {
                Text(
                    text = habit.text,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                    maxLines = 2,
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Button(
                text = "did it",
                onClick = actionRunCallback<MarkDoneAction>(MarkDoneAction.parameters(habit)),
            )
        }
    }
}

@Composable
private fun Resting() {
    Text(
        text = "nothing doable right now",
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp),
    )
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
                entryPoint.completionRecorder().complete(habitId, parameters[VARIATION_ID])
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
    fun completionRecorder(): WidgetCompletionRecorder
    fun widgetRefresher(): WidgetRefresher
    fun spriteResolver(): SpriteResolver
}
