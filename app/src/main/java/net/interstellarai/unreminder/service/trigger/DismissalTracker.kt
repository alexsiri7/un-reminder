package net.interstellarai.unreminder.service.trigger

import android.util.Log
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.notification.NotificationHelper
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
        if (!allDismissed) {
            Log.d(TAG, "Habit $habitId streak broken (not all of last $STREAK_THRESHOLD are DISMISSED), skipping")
            return
        }

        val habit = habitRepository.getByIdOnce(habitId) ?: run {
            Log.w(TAG, "Habit $habitId not found, cannot deactivate")
            return
        }

        if (!habit.active) {
            Log.d(TAG, "Habit $habitId is already paused, skipping")
            return
        }

        if (habit.dedicationLevel > 0 && habit.autoAdjustLevel) {
            Log.i(TAG, "Habit ${habit.name} demoted to level ${habit.dedicationLevel - 1}")
            habitRepository.update(habit.copy(dedicationLevel = habit.dedicationLevel - 1))
        } else if (habit.dedicationLevel == 0 && habit.autoAdjustLevel) {
            Log.i(TAG, "Habit ${habit.name} at level 0 with $STREAK_THRESHOLD consecutive DISMISSEDs — pausing")
            habitRepository.update(habit.copy(active = false))
            notificationHelper.postHabitPausedNotification(habitId, habit.name)
        } else {
            Log.d(TAG, "Habit ${habit.name} autoAdjustLevel disabled — skipping demotion/pause (level=${habit.dedicationLevel})")
        }
    }

    suspend fun onCompleted(triggerId: Long) {
        val trigger = triggerRepository.getById(triggerId) ?: run {
            Log.w(TAG, "Trigger $triggerId not found, skipping completion check")
            return
        }
        val habitId = trigger.habitId ?: run {
            Log.d(TAG, "Trigger $triggerId has no habitId, skipping completion check")
            return
        }
        val habit = habitRepository.getByIdOnce(habitId) ?: run {
            Log.w(TAG, "Habit $habitId not found, cannot promote")
            return
        }
        if (!habit.autoAdjustLevel) {
            Log.d(TAG, "Habit ${habit.name} has autoAdjustLevel disabled, skipping promotion")
            return
        }
        if (habit.dedicationLevel >= 5) {
            Log.d(TAG, "Habit ${habit.name} already at max level 5, skipping promotion")
            return
        }

        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 24 * 3600 * 1000
        val fourteenDaysAgo = now - 14L * 24 * 3600 * 1000
        val twentyEightDaysAgo = now - 28L * 24 * 3600 * 1000

        // Promotion thresholds: ~1 completion/2 days at each tier, window doubles per level.
        //   L0 → 1: immediate (first completion unblocks)
        //   L1 → 2: 3 in 7 days  (~3/week)
        //   L2 → 3: 5 in 7 days  (~5/week)
        //   L3 → 4: 10 in 14 days (~5/week, sustained)
        //   L4 → 5: 20 in 28 days (~5/week, sustained over a month)
        val shouldPromote = when (habit.dedicationLevel) {
            0 -> true
            1 -> triggerRepository.getCompletionsSince(habitId, sevenDaysAgo).size >= 3
            2 -> triggerRepository.getCompletionsSince(habitId, sevenDaysAgo).size >= 5
            3 -> triggerRepository.getCompletionsSince(habitId, fourteenDaysAgo).size >= 10
            4 -> triggerRepository.getCompletionsSince(habitId, twentyEightDaysAgo).size >= 20
            else -> false
        }

        if (shouldPromote) {
            habitRepository.update(habit.copy(dedicationLevel = habit.dedicationLevel + 1))
            Log.i(TAG, "Habit ${habit.name} promoted to level ${habit.dedicationLevel + 1}")
        }
    }
}
