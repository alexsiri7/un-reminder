package com.alexsiri7.unreminder.data.repository

import com.alexsiri7.unreminder.data.db.HabitDao
import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.data.db.HabitLocationCrossRef
import com.alexsiri7.unreminder.data.db.HabitLocationCrossRefDao
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val crossRefDao: HabitLocationCrossRefDao
) {
    fun getAll(): Flow<List<HabitEntity>> = habitDao.getAll()

    fun getAllActive(): Flow<List<HabitEntity>> = habitDao.getAllActive()

    fun getById(id: Long): Flow<HabitEntity?> = habitDao.getById(id)

    suspend fun insert(habit: HabitEntity): Long = habitDao.insert(habit)

    suspend fun update(habit: HabitEntity) = habitDao.update(habit.copy(updatedAt = Instant.now()))

    suspend fun delete(habit: HabitEntity) {
        crossRefDao.deleteByHabitId(habit.id)
        habitDao.delete(habit)
    }

    suspend fun getEligibleHabits(
        currentLocationIds: Set<Long>,
        excludeRecentMinutes: Long = 90
    ): List<HabitEntity> {
        val cutoff = Instant.now().minusSeconds(excludeRecentMinutes * 60).toEpochMilli()
        val ids = if (currentLocationIds.isEmpty()) listOf(-1L) else currentLocationIds.toList()
        return habitDao.getEligibleHabits(ids, cutoff)
    }

    suspend fun getLocationIds(habitId: Long): List<Long> =
        crossRefDao.getLocationIdsForHabit(habitId)

    suspend fun setLocations(habitId: Long, locationIds: Set<Long>) {
        crossRefDao.deleteByHabitId(habitId)
        for (locId in locationIds) {
            crossRefDao.insert(HabitLocationCrossRef(habitId = habitId, locationId = locId))
        }
    }
}
