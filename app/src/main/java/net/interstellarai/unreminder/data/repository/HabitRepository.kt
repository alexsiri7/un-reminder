package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.HabitDao
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.HabitLocationCrossRef
import net.interstellarai.unreminder.data.db.HabitLocationCrossRefDao
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

    suspend fun delete(habit: HabitEntity) = habitDao.delete(habit)

    suspend fun getByIdOnce(id: Long): HabitEntity? = habitDao.getByIdOnce(id)

    suspend fun getEligibleHabits(
        currentLocationIds: Set<Long>,
        excludeRecentMinutes: Long = 90
    ): List<HabitEntity> {
        val cutoff = Instant.now().minusSeconds(excludeRecentMinutes * 60).toEpochMilli()
        // Room crashes if IN-clause receives an empty list. Use an impossible ID (-1) so the
        // clause is syntactically valid but never matches any real habit row.
        val ids = if (currentLocationIds.isEmpty()) listOf(-1L) else currentLocationIds.toList()
        return habitDao.getEligibleHabits(ids, cutoff)
    }

    suspend fun getLocationIds(habitId: Long): List<Long> =
        crossRefDao.getLocationIdsForHabit(habitId)

    suspend fun setLocations(habitId: Long, locationIds: Set<Long>) {
        crossRefDao.deleteByHabitId(habitId)
        crossRefDao.insertAll(locationIds.map { HabitLocationCrossRef(habitId = habitId, locationId = it) })
    }
}
