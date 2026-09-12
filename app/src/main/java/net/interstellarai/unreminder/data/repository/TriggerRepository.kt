package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.TriggerDao
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.domain.model.TriggerStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TriggerRepository @Inject constructor(
    private val triggerDao: TriggerDao
) {
    fun getRecentTriggers(limit: Int = 20): Flow<List<TriggerEntity>> =
        triggerDao.getRecentTriggers(limit)

    suspend fun insert(trigger: TriggerEntity): Long = triggerDao.insert(trigger)

    suspend fun getById(id: Long): TriggerEntity? = triggerDao.getById(id)

    suspend fun getAllScheduled(): List<TriggerEntity> = triggerDao.getAllScheduled()

    suspend fun getFiredIds(): List<Long> = triggerDao.getFiredIds()

    suspend fun updateFired(
        id: Long,
        habitId: Long,
        prompt: String
    ) {
        triggerDao.updateFired(
            id = id,
            status = TriggerStatus.FIRED.name,
            firedAt = Instant.now().toEpochMilli(),
            habitId = habitId,
            prompt = prompt
        )
    }

    suspend fun updateOutcome(id: Long, status: TriggerStatus) {
        triggerDao.updateStatus(id, status.name)
    }

    suspend fun deleteScheduledOlderThan(cutoff: Instant) =
        triggerDao.deleteScheduledOlderThan(cutoff.toEpochMilli())

    suspend fun getLastNForHabit(habitId: Long, n: Int): List<TriggerEntity> =
        triggerDao.getLastNForHabit(habitId, n)

    suspend fun getLastFiredForHabit(habitId: Long): Long? = triggerDao.getLastFiredForHabit(habitId)

    suspend fun getCompletionsSince(habitId: Long, sinceMillis: Long): List<TriggerEntity> =
        triggerDao.getCompletionsSince(habitId, sinceMillis)

    suspend fun getLastFiredOrDismissedForHabit(habitId: Long): Long? =
        triggerDao.getLastFiredOrDismissedForHabit(habitId)

    suspend fun countCompletedSince(habitId: Long, sinceMillis: Long): Int =
        triggerDao.countCompletedSince(habitId, sinceMillis)

    suspend fun countDailyCompletionsSince(habitId: Long, sinceMillis: Long): Int =
        triggerDao.countDailyCompletionsSince(habitId, sinceMillis)

    // The zone offset and today's boundary below are resolved when the flow is built, so a
    // collector that outlives midnight or a zone change needs to re-collect.
    fun daysWithAnyCompletion(): Flow<Int> =
        triggerDao.daysWithAnyCompletion(currentZoneOffsetMillis())

    fun daysWithHabitCompletion(habitId: Long): Flow<Int> =
        triggerDao.daysWithHabitCompletion(habitId, currentZoneOffsetMillis())

    fun totalCompletionsForHabit(habitId: Long): Flow<Int> =
        triggerDao.totalCompletionsForHabit(habitId)

    fun hasCompletedAnythingToday(): Flow<Boolean> =
        triggerDao.countAnyCompletionsSince(startOfTodayMillis()).map { it > 0 }

    private fun currentZoneOffsetMillis(): Long =
        ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds * 1000L

    private fun startOfTodayMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
