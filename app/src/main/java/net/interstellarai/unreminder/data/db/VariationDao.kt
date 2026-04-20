package net.interstellarai.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VariationDao {

    /**
     * Returns up to [limit] unconsumed variations for [habitId].
     * The limit of 50 is the variation pool size — keep this coordinated
     * with [net.interstellarai.unreminder.data.repository.VariationRepository.pickRandomUnused],
     * which passes 50 to bound its random selection.
     */
    @Query("SELECT * FROM variation WHERE habit_id = :habitId AND consumed_at IS NULL LIMIT :limit")
    suspend fun getUnusedForHabit(habitId: Long, limit: Int): List<VariationEntity>

    /** Returns the number of rows updated (1 on success, 0 if already consumed or deleted). */
    @Query("UPDATE variation SET consumed_at = :at WHERE id = :id AND consumed_at IS NULL")
    suspend fun markConsumed(id: Long, at: Long): Int

    @Query("SELECT COUNT(*) FROM variation WHERE habit_id = :habitId AND consumed_at IS NULL")
    suspend fun countUnused(habitId: Long): Int

    @Query("DELETE FROM variation WHERE habit_id = :habitId")
    suspend fun deleteByHabit(habitId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(variants: List<VariationEntity>)
}
