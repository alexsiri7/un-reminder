package com.alexsiri7.unreminder.data.repository

import com.alexsiri7.unreminder.data.db.TriggerDao
import com.alexsiri7.unreminder.data.db.TriggerEntity
import com.alexsiri7.unreminder.domain.model.TriggerStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
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

    suspend fun deleteAllScheduled() = triggerDao.deleteAllScheduled()

    suspend fun getLastNForHabit(habitId: Long, n: Int): List<TriggerEntity> =
        triggerDao.getLastNForHabit(habitId, n)
}
