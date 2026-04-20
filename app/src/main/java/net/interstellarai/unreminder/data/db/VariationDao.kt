package net.interstellarai.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VariationDao {

    @Query("SELECT * FROM variation WHERE habit_id = :habitId AND consumed_at IS NULL LIMIT :limit")
    suspend fun getUnusedForHabit(habitId: Long, limit: Int = 50): List<VariationEntity>

    @Query("UPDATE variation SET consumed_at = :at WHERE id = :id")
    suspend fun markConsumed(id: Long, at: Long)

    @Query("SELECT COUNT(*) FROM variation WHERE habit_id = :habitId AND consumed_at IS NULL")
    suspend fun countUnused(habitId: Long): Int

    @Query("DELETE FROM variation WHERE habit_id = :habitId")
    suspend fun deleteByHabit(habitId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(variants: List<VariationEntity>)
}
