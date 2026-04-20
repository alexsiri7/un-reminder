package net.interstellarai.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface HabitWindowCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<HabitWindowCrossRef>)

    @Query("DELETE FROM habit_window WHERE habit_id = :habitId")
    suspend fun deleteByHabitId(habitId: Long)

    @Query("SELECT window_id FROM habit_window WHERE habit_id = :habitId")
    suspend fun getWindowIdsForHabit(habitId: Long): List<Long>

    @Transaction
    suspend fun replaceAll(habitId: Long, crossRefs: List<HabitWindowCrossRef>) {
        deleteByHabitId(habitId)
        insertAll(crossRefs)
    }
}
