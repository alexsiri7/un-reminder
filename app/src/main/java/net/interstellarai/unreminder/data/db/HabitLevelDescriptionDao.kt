package net.interstellarai.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HabitLevelDescriptionDao {
    @Query("SELECT * FROM habit_level_descriptions WHERE habit_id = :habitId ORDER BY level ASC")
    suspend fun getForHabit(habitId: Long): List<HabitLevelDescriptionEntity>

    @Query("SELECT * FROM habit_level_descriptions WHERE habit_id = :habitId AND level = :level")
    suspend fun getForLevel(habitId: Long, level: Int): HabitLevelDescriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(descriptions: List<HabitLevelDescriptionEntity>)

    @Query("DELETE FROM habit_level_descriptions WHERE habit_id = :habitId")
    suspend fun deleteByHabit(habitId: Long)
}
