package net.interstellarai.unreminder.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place the rest of the app asks the home-screen widget to catch up with what is
 * doable. Anything that changes eligibility (a trigger firing, an outcome landing, a
 * geofence transition, a window opening or closing, a reboot) calls [refresh].
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val picker: DoableHabitPicker,
    private val windowRepository: WindowRepository,
    private val triggerRepository: TriggerRepository,
) {
    companion object {
        const val WORK_NAME = "widget_refresh"
        const val WINDOW_BOUNDARY_WORK_NAME = "widget_refresh_window_boundary"
    }

    /** Fire-and-forget; safe from receivers, view models and workers alike. */
    fun refresh() {
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build(),
        )
    }

    /**
     * Re-picks for every placed widget, snapshots today's progress alongside it, and pushes
     * both to the launcher in one update.
     */
    suspend fun refreshNow() {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(DoableHabitWidget::class.java)
        if (ids.isEmpty()) return
        val habit = picker.pick()
        val progress = dayProgress()
        val widget = DoableHabitWidget()
        for (id in ids) {
            updateAppWidgetState(context, id) { DoableHabitWidget.store(it, habit, progress) }
            widget.update(context, id)
        }
        scheduleRefreshAtNextWindowBoundary()
    }

    // Built fresh on every refresh rather than collected once: the repository resolves "today"
    // when the flow is created, so this is what makes the tick after midnight read as not yet.
    internal suspend fun dayProgress(): DayProgress = DayProgress(
        completedToday = triggerRepository.hasCompletedAnythingToday().first(),
        daysWithAnyCompletion = triggerRepository.daysWithAnyCompletion().first(),
    )

    // Nothing else in the app fires when a window opens or closes, so each refresh arms a
    // single wake-up at the next boundary instead of polling for it.
    internal suspend fun scheduleRefreshAtNextWindowBoundary(now: LocalDateTime = LocalDateTime.now()) {
        val boundary = WindowBoundaries.next(windowRepository.getActiveWindows(), now)
        if (boundary == null) {
            workManager.cancelUniqueWork(WINDOW_BOUNDARY_WORK_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(Duration.between(now, boundary))
            .build()
        workManager.enqueueUniqueWork(WINDOW_BOUNDARY_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
