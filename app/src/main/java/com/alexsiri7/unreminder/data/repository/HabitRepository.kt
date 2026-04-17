package com.alexsiri7.unreminder.data.repository

import com.alexsiri7.unreminder.data.db.HabitDao
import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.domain.model.LocationTag
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao
) {
    fun getAll(): Flow<List<HabitEntity>> = habitDao.getAll()

    fun getAllActive(): Flow<List<HabitEntity>> = habitDao.getAllActive()

    fun getById(id: Long): Flow<HabitEntity?> = habitDao.getById(id)

    suspend fun insert(habit: HabitEntity): Long = habitDao.insert(habit)

    suspend fun update(habit: HabitEntity) = habitDao.update(habit.copy(updatedAt = Instant.now()))

    suspend fun delete(habit: HabitEntity) = habitDao.delete(habit)

    suspend fun getEligibleHabits(
        locationTag: LocationTag,
        excludeRecentMinutes: Long = 90
    ): List<HabitEntity> {
        val cutoff = Instant.now().minusSeconds(excludeRecentMinutes * 60).toEpochMilli()
        return habitDao.getEligibleHabits(locationTag.name, cutoff)
    }
}
