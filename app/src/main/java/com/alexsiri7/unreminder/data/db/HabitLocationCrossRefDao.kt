package com.alexsiri7.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HabitLocationCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<HabitLocationCrossRef>)

    @Query("DELETE FROM habit_location WHERE habit_id = :habitId")
    suspend fun deleteByHabitId(habitId: Long)

    @Query("SELECT location_id FROM habit_location WHERE habit_id = :habitId")
    suspend fun getLocationIdsForHabit(habitId: Long): List<Long>
}
