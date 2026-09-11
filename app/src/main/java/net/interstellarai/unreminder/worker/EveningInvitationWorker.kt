package net.interstellarai.unreminder.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import net.interstellarai.unreminder.data.repository.EveningInvitationRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.domain.isDoableNow
import net.interstellarai.unreminder.service.notification.EveningInvitationWording
import net.interstellarai.unreminder.service.notification.NotificationHelper
import java.time.LocalDate
import java.time.LocalTime

@HiltWorker
class EveningInvitationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: EveningInvitationRepository,
    private val triggerRepository: TriggerRepository,
    private val habitRepository: HabitRepository,
    private val availabilityService: HabitAvailabilityService,
    private val notificationHelper: NotificationHelper,
    private val scheduler: EveningInvitationScheduler,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "EveningInvitationWorker"
    }

    override suspend fun doWork(): Result {
        try {
            val settings = repository.settings.first()
            if (!settings.enabled) {
                Log.d(TAG, "Evening invitation switched off, not rescheduling")
                return Result.success()
            }
            val today = LocalDate.now()
            when {
                LocalTime.now().isBefore(settings.time) ->
                    Log.d(TAG, "Ran before ${settings.time} (deferred past midnight?), skipping")
                repository.lastPostedDate() == today ->
                    Log.d(TAG, "Already posted today, skipping")
                triggerRepository.hasCompletedAnythingToday().first() ->
                    Log.d(TAG, "Something was completed today, skipping")
                !anythingDoableNow() ->
                    Log.d(TAG, "Nothing doable right now, skipping")
                else -> {
                    notificationHelper.postEveningInvitation(EveningInvitationWording.body(today))
                    repository.markPostedOn(today)
                }
            }
        } catch (e: CancellationException) {
            // Not rescheduling here: WorkManager may not accept new work during cancellation.
            // ensureScheduled() on next app start or boot re-enqueues after the CANCELLED state.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Evening invitation worker failed", e)
            Sentry.captureException(e) { scope ->
                scope.setTag("component", "evening-invitation-worker")
            }
        }
        scheduler.reschedule()
        return Result.success()
    }

    private suspend fun anythingDoableNow(): Boolean {
        val habits = habitRepository.getAll().first()
        if (habits.isEmpty()) return false
        return availabilityService.computeForAll(habits).values.any { it.isDoableNow }
    }
}
