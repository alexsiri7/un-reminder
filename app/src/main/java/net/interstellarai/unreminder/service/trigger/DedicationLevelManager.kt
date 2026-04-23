package net.interstellarai.unreminder.service.trigger

import android.util.Log
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DedicationLevelManager @Inject constructor(
    private val triggerRepository: TriggerRepository,
    private val habitRepository: HabitRepository,
) {
    companion object {
        private const val TAG = "DedicationLevelManager"
        const val MAX_LEVEL = 5
        // (completionsRequired, windowDays) per current level
        private val THRESHOLDS = mapOf(
            0 to Pair(3, 7),
            1 to Pair(5, 14),
            2 to Pair(7, 21),
            3 to Pair(10, 30),
            4 to Pair(14, 45),
        )
    }

    suspend fun maybePromote(habitId: Long) {
        if (habitId <= 0) return

        val habit = habitRepository.getByIdOnce(habitId) ?: run {
            Log.w(TAG, "Habit $habitId not found, skipping promotion check")
            return
        }

        if (!habit.autoAdjustLevel) {
            Log.d(TAG, "Habit $habitId has autoAdjustLevel=false, skipping")
            return
        }
        if (habit.dedicationLevel >= MAX_LEVEL) {
            Log.d(TAG, "Habit $habitId already at max level, skipping")
            return
        }

        val threshold = THRESHOLDS[habit.dedicationLevel] ?: return
        val (required, windowDays) = threshold
        val sinceMillis = Instant.now().minusSeconds(windowDays.toLong() * 86_400L).toEpochMilli()
        val count = triggerRepository.countCompletionsSince(habitId, sinceMillis)

        Log.d(TAG, "Habit $habitId level=${habit.dedicationLevel} completions=$count required=$required in ${windowDays}d")

        if (count >= required) {
            val newLevel = habit.dedicationLevel + 1
            Log.i(TAG, "Promoting habit ${habit.name} from level ${habit.dedicationLevel} to $newLevel")
            habitRepository.update(habit.copy(dedicationLevel = newLevel))
        }
    }
}
