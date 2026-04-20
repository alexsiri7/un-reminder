package net.interstellarai.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface HabitLevelDescriptionDao {

    @Query("SELECT * FROM habit_level_descriptions WHERE habit_id = :habitId ORDER BY level ASC")
    suspend fun getForHabit(habitId: Long): List<HabitLevelDescriptionEntity>

    @Query("SELECT * FROM habit_level_descriptions WHERE habit_id = :habitId AND level = :level LIMIT 1")
    suspend fun getForHabitAndLevel(habitId: Long, level: Int): HabitLevelDescriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<HabitLevelDescriptionEntity>)

    @Query("DELETE FROM habit_level_descriptions WHERE habit_id = :habitId")
    suspend fun deleteByHabit(habitId: Long)

    @Transaction
    suspend fun deleteAndInsertForHabit(habitId: Long, entries: List<HabitLevelDescriptionEntity>) {
        deleteByHabit(habitId)
        upsertAll(entries)
    }
}
