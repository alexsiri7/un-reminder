package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.HabitDao
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.HabitLocationCrossRef
import net.interstellarai.unreminder.data.db.HabitLocationCrossRefDao
import net.interstellarai.unreminder.data.db.HabitWindowCrossRef
import net.interstellarai.unreminder.data.db.HabitWindowCrossRefDao
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val crossRefDao: HabitLocationCrossRefDao,
    private val windowCrossRefDao: HabitWindowCrossRefDao
) {
    fun getAll(): Flow<List<HabitEntity>> = habitDao.getAll()

    fun getAllActive(): Flow<List<HabitEntity>> = habitDao.getAllActive()

    fun getById(id: Long): Flow<HabitEntity?> = habitDao.getById(id)

    suspend fun insert(habit: HabitEntity): Long = habitDao.insert(habit)

    suspend fun update(habit: HabitEntity) = habitDao.update(habit.copy(updatedAt = Instant.now()))

    suspend fun delete(habit: HabitEntity) = habitDao.delete(habit)

    suspend fun getByIdOnce(id: Long): HabitEntity? = habitDao.getByIdOnce(id)

    suspend fun getEligibleHabits(
        currentLocationIds: Set<Long>
    ): List<HabitEntity> {
        // COMPLETED today: excluded until midnight (done for the day).
        val completedCutoff = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        // DISMISSED or FIRED-but-not-responded: 3-hour cooldown.
        val dismissedCutoff = Instant.now().minusSeconds(3 * 3600L).toEpochMilli()
        // Room crashes if IN-clause receives an empty list. Use an impossible ID (-1) so the
        // clause is syntactically valid but never matches any real habit row.
        val ids = if (currentLocationIds.isEmpty()) listOf(-1L) else currentLocationIds.toList()
        val currentSecondOfDay = LocalTime.now().toSecondOfDay()
        val dayOfWeekBit = 1 shl (LocalDate.now().dayOfWeek.value - 1)
        return habitDao.getEligibleHabits(ids, completedCutoff, dismissedCutoff, currentSecondOfDay, dayOfWeekBit)
    }

    suspend fun getLocationIds(habitId: Long): List<Long> =
        crossRefDao.getLocationIdsForHabit(habitId)

    suspend fun setLocations(habitId: Long, locationIds: Set<Long>) {
        crossRefDao.deleteByHabitId(habitId)
        crossRefDao.insertAll(locationIds.map { HabitLocationCrossRef(habitId = habitId, locationId = it) })
    }

    suspend fun getWindowIds(habitId: Long): List<Long> =
        windowCrossRefDao.getWindowIdsForHabit(habitId)

    suspend fun setWindows(habitId: Long, windowIds: Set<Long>) {
        windowCrossRefDao.deleteByHabitId(habitId)
        windowCrossRefDao.insertAll(windowIds.map { HabitWindowCrossRef(habitId = habitId, windowId = it) })
    }
}
