package net.interstellarai.unreminder.service.trigger

import android.util.Log
import kotlinx.coroutines.CancellationException
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DedicationLevelManager @Inject constructor(
    private val habitRepository: HabitRepository,
    private val triggerRepository: TriggerRepository,
) {
    companion object {
        private const val TAG = "DedicationLevelManager"
        const val MAX_LEVEL = 5
    }

    suspend fun maybePromote(habit: HabitEntity) {
        if (!habit.autoAdjustLevel) return
        if (habit.dedicationLevel >= MAX_LEVEL) return

        val now = Instant.now()
        val promoted = when (habit.dedicationLevel) {
            0 -> true
            1 -> countSince(habit.id, now, days = 7) >= 3
            2 -> countSince(habit.id, now, days = 7) >= 5
            3 -> countSince(habit.id, now, days = 14) >= 10
            4 -> countSince(habit.id, now, days = 28) >= 20
            else -> false
        }

        if (promoted) {
            try {
                habitRepository.update(habit.copy(dedicationLevel = habit.dedicationLevel + 1))
                Log.d(TAG, "Promoted habit ${habit.id} to level ${habit.dedicationLevel + 1}")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Failed to promote habit ${habit.id}", e)
            }
        }
    }

    private suspend fun countSince(habitId: Long, now: Instant, days: Long): Int {
        val sinceMillis = now.minus(days, ChronoUnit.DAYS).toEpochMilli()
        return triggerRepository.countCompletionsSince(habitId, sinceMillis)
    }
}
