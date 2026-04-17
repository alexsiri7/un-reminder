package com.alexsiri7.unreminder.data.db

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

    @Query("SELECT * FROM habits ORDER BY name ASC")
    fun getAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE active = 1 ORDER BY name ASC")
    fun getAllActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE active = 1")
    suspend fun getAllActiveList(): List<HabitEntity>

    @Query(
        """
        SELECT * FROM habits
        WHERE active = 1
        AND (location_tag = :locationTag OR location_tag = 'ANYWHERE')
        AND id NOT IN (
            SELECT habit_id FROM triggers
            WHERE habit_id IS NOT NULL
            AND fired_at IS NOT NULL
            AND fired_at > :excludeAfter
        )
        """
    )
    suspend fun getEligibleHabits(locationTag: String, excludeAfter: Long): List<HabitEntity>
}
