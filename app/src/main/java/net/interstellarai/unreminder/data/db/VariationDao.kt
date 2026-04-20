package net.interstellarai.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.Instant

@Dao
interface VariationDao {

    /** Returns up to [limit] unconsumed variations for [habitId]. */
    @Query("SELECT * FROM variations WHERE habit_id = :habitId AND consumed_at IS NULL LIMIT :limit")
    suspend fun getUnusedForHabit(habitId: Long, limit: Int): List<VariationEntity>

    /** Returns the number of rows updated (1 on success, 0 if already consumed or deleted). */
    @Query("UPDATE variations SET consumed_at = :at WHERE id = :id AND consumed_at IS NULL")
    suspend fun markConsumed(id: Long, at: Instant): Int

    @Query("SELECT COUNT(*) FROM variations WHERE habit_id = :habitId AND consumed_at IS NULL")
    suspend fun countUnused(habitId: Long): Int

    @Query("DELETE FROM variations WHERE habit_id = :habitId")
    suspend fun deleteByHabit(habitId: Long)

    /** Inserts variations, silently ignoring duplicates that match the unique (habit_id, prompt_fingerprint, text) index. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(variants: List<VariationEntity>)
}
