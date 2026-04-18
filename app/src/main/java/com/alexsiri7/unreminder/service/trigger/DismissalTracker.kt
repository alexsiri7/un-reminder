package com.alexsiri7.unreminder.service.trigger

import android.util.Log
import com.alexsiri7.unreminder.data.repository.HabitRepository
import com.alexsiri7.unreminder.data.repository.TriggerRepository
import com.alexsiri7.unreminder.domain.model.TriggerStatus
import com.alexsiri7.unreminder.service.notification.NotificationHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DismissalTracker @Inject constructor(
    private val triggerRepository: TriggerRepository,
    private val habitRepository: HabitRepository,
    private val notificationHelper: NotificationHelper
) {
    companion object {
        private const val TAG = "DismissalTracker"
        const val STREAK_THRESHOLD = 3
    }

    suspend fun onDismissed(triggerId: Long) {
        val trigger = triggerRepository.getById(triggerId) ?: run {
            Log.w(TAG, "Trigger $triggerId not found, skipping dismissal check")
            return
        }
        val habitId = trigger.habitId ?: run {
            Log.d(TAG, "Trigger $triggerId has no habitId, skipping dismissal check")
            return
        }

        val recent = triggerRepository.getLastNForHabit(habitId, STREAK_THRESHOLD)
        if (recent.size < STREAK_THRESHOLD) {
            Log.d(TAG, "Habit $habitId has fewer than $STREAK_THRESHOLD recorded triggers, skipping")
            return
        }

        val allDismissed = recent.all { it.status == TriggerStatus.DISMISSED }
        if (!allDismissed) return

        val habit = habitRepository.getByIdOnce(habitId) ?: run {
            Log.w(TAG, "Habit $habitId not found, cannot deactivate")
            return
        }

        Log.i(TAG, "Habit ${habit.name} has $STREAK_THRESHOLD consecutive DISMISSEDs — pausing")
        habitRepository.update(habit.copy(active = false))
        notificationHelper.postHabitPausedNotification(habitId, habit.name)
    }
}
