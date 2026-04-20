package net.interstellarai.unreminder.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Query("SELECT * FROM habits WHERE id = :id")
    fun getById(id: Long): Flow<HabitEntity?>

    /** One-shot suspend read; use when a reactive Flow is not needed (e.g., background coroutine). */
    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getByIdOnce(id: Long): HabitEntity?

    @Query("SELECT * FROM habits ORDER BY name ASC")
    fun getAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE active = 1 ORDER BY name ASC")
    fun getAllActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE active = 1")
    suspend fun getAllActiveList(): List<HabitEntity>

    @Query("""
        SELECT DISTINCT h.* FROM habits h
        WHERE h.active = 1
        AND (
            NOT EXISTS (SELECT 1 FROM habit_location hl WHERE hl.habit_id = h.id)
            OR EXISTS (
                SELECT 1 FROM habit_location hl
                WHERE hl.habit_id = h.id AND hl.location_id IN (:locationIds)
            )
        )
        AND (
            NOT EXISTS (SELECT 1 FROM habit_window hw WHERE hw.habit_id = h.id)
            OR EXISTS (
                SELECT 1 FROM habit_window hw
                JOIN windows w ON w.id = hw.window_id
                WHERE hw.habit_id = h.id
                  AND w.active = 1
                  -- NOTE: overnight windows (end_time < start_time) are not supported;
                  -- start_time/end_time are seconds-of-day and must satisfy start_time <= end_time.
                  AND w.start_time <= :currentSecondOfDay
                  AND w.end_time >= :currentSecondOfDay
                  AND (w.days_of_week_bitmask & :dayOfWeekBit) != 0
            )
        )
        AND h.id NOT IN (
            SELECT habit_id FROM triggers
            WHERE habit_id IS NOT NULL
            AND fired_at IS NOT NULL
            AND fired_at > :excludeAfter
        )
    """)
    suspend fun getEligibleHabits(
        locationIds: List<Long>,
        excludeAfter: Long,
        currentSecondOfDay: Int,
        dayOfWeekBit: Int
    ): List<HabitEntity>
}
