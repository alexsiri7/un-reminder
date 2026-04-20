package net.interstellarai.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface VariationDao {

    /** Returns up to [limit] unconsumed variations for [habitId]. */
    @Query("SELECT * FROM variations WHERE habit_id = :habitId AND consumed_at IS NULL ORDER BY RANDOM() LIMIT :limit")
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

    /** Reactive list of all unused variations for [habitId], newest first. */
    @Query("SELECT * FROM variations WHERE habit_id = :habitId AND consumed_at IS NULL ORDER BY generated_at DESC")
    fun getUnusedFlow(habitId: Long): Flow<List<VariationEntity>>

    /** Reactive list of the most recently consumed variations for [habitId]. */
    @Query("SELECT * FROM variations WHERE habit_id = :habitId AND consumed_at IS NOT NULL ORDER BY consumed_at DESC LIMIT :limit")
    fun getRecentlyUsedFlow(habitId: Long, limit: Int): Flow<List<VariationEntity>>

    /** Deletes a single variation by [id]. */
    @Query("DELETE FROM variations WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Reactive total count of all variations (used + unused) for [habitId]. */
    @Query("SELECT COUNT(*) FROM variations WHERE habit_id = :habitId")
    fun countTotalFlow(habitId: Long): Flow<Int>

    /** Reactive count of variations consumed at or after [dayStart] for [habitId]. */
    @Query("SELECT COUNT(*) FROM variations WHERE habit_id = :habitId AND consumed_at >= :dayStart")
    fun countConsumedSinceFlow(habitId: Long, dayStart: Instant): Flow<Int>
}
