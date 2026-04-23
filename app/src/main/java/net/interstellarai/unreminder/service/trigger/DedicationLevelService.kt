package net.interstellarai.unreminder.service.trigger

import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DedicationLevelService @Inject constructor(
    private val habitRepository: HabitRepository,
    private val triggerRepository: TriggerRepository
) {
    companion object {
        const val MAX_LEVEL = 5
    }

    suspend fun evaluatePromotion(habitId: Long) {
        val habit = habitRepository.getByIdOnce(habitId) ?: return
        val currentLevel = habit.dedicationLevel
        if (currentLevel >= MAX_LEVEL) return

        val shouldPromote = when (currentLevel) {
            0 -> true
            1 -> completionsInLastNDays(habitId, 7) >= 3
            2 -> completionsInLastNDays(habitId, 7) >= 5
            3 -> completionsInLastNDays(habitId, 14) >= 10
            4 -> completionsInLastNDays(habitId, 30) >= 20
            else -> false
        }

        if (shouldPromote) {
            habitRepository.update(habit.copy(dedicationLevel = currentLevel + 1))
        }
    }

    /** Stub -- demotion is out of scope for this PR. */
    @Suppress("unused")
    suspend fun checkDemotion(habitId: Long) { /* TODO in follow-up issue */ }

    private suspend fun completionsInLastNDays(habitId: Long, days: Long): Int {
        val since = Instant.now().minus(days, ChronoUnit.DAYS)
        return triggerRepository.getLastNForHabit(habitId, 60)
            .count { it.status == TriggerStatus.COMPLETED && it.firedAt != null && it.firedAt > since }
    }
}
