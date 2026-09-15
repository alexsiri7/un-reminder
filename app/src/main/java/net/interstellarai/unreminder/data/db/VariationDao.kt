package net.interstellarai.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface VariationDao {

    /**
     * Returns up to [limit] unconsumed variations for [habitId]: first those written for the
     * mode [modeBit] names, then mode-neutral ones, then those written for another mode, so
     * a habit always has something to say even when nothing suits the moment. Within each of
     * those, rows sharing the shape of the habit's most recently consumed variation sort
     * last, so the head of the list is a fresh shape whenever one is available and the same
     * shape is the fallback for a nearly drained pool. Within a shape rank, rows that would
     * repeat that variation's look on any surface sort last: a look is `id mod n` over the
     * surface's entries (VariantTreatment), where 4 is the notification's style count and 5
     * the widget's and detail screen's layout count, each pinned by its enum's test. The rest
     * is random order. Both tiers read the one last consumed row [deleteConsumedByHabit] keeps,
     * joined so that with nothing consumed the comparisons are NULL and rank nothing.
     */
    @Query(
        "WITH last_consumed AS (" +
        "SELECT shape AS last_shape, id AS last_id FROM variations " +
        "WHERE habit_id = :habitId AND consumed_at IS NOT NULL ORDER BY consumed_at DESC LIMIT 1" +
        ") " +
        "SELECT v.* FROM variations v LEFT JOIN last_consumed ON 1 = 1 " +
        "WHERE v.habit_id = :habitId AND v.consumed_at IS NULL " +
        "ORDER BY CASE WHEN (v.modes & :modeBit) != 0 THEN 0 WHEN v.modes = 0 THEN 1 ELSE 2 END, " +
        "CASE WHEN v.shape = last_shape THEN 1 ELSE 0 END, " +
        "CASE WHEN v.id % 4 = last_id % 4 OR v.id % 5 = last_id % 5 THEN 1 ELSE 0 END, " +
        "RANDOM() LIMIT :limit"
    )
    suspend fun getUnusedForHabit(habitId: Long, modeBit: Int, limit: Int): List<VariationEntity>

    /** Returns the number of rows updated (1 on success, 0 if already consumed or deleted). */
    @Query("UPDATE variations SET consumed_at = :at WHERE id = :id AND consumed_at IS NULL")
    suspend fun markConsumed(id: Long, at: Instant): Int

    /**
     * The row by [id], consumed or not: the words tapped on the Now page or widget must still
     * open after a trigger consumed them in the meantime. Null once a refill has pruned it.
     */
    @Query("SELECT * FROM variations WHERE id = :id")
    suspend fun getById(id: Long): VariationEntity?

    @Query("SELECT COUNT(*) FROM variations WHERE habit_id = :habitId AND consumed_at IS NULL")
    suspend fun countUnused(habitId: Long): Int

    @Query("DELETE FROM variations WHERE habit_id = :habitId")
    suspend fun deleteByHabit(habitId: Long)

    /**
     * Prunes consumed variations for [habitId] except the most recently consumed one, which
     * [getUnusedForHabit] still needs as the shape and look to rotate away from after a refill.
     */
    @Query(
        "DELETE FROM variations WHERE habit_id = :habitId AND consumed_at IS NOT NULL AND id != (" +
        "SELECT id FROM variations WHERE habit_id = :habitId AND consumed_at IS NOT NULL " +
        "ORDER BY consumed_at DESC LIMIT 1)"
    )
    suspend fun deleteConsumedByHabit(habitId: Long)

    /**
     * Removes the unconsumed part of [habitId]'s pool that was generated under a version other
     * than [version]. Rows at [VariationEntity.UNVERSIONED] count as another version, so the
     * pre-version rows go on the first versioned refill.
     */
    @Query("DELETE FROM variations WHERE habit_id = :habitId AND consumed_at IS NULL AND generation_version != :version")
    suspend fun deleteUnusedNotAtVersion(habitId: Long, version: Int)

    /** Removes the whole unconsumed pool for [habitId], whatever version it was generated under. */
    @Query("DELETE FROM variations WHERE habit_id = :habitId AND consumed_at IS NULL")
    suspend fun deleteUnusedByHabit(habitId: Long)

    /**
     * Active habits whose pool — its unconsumed rows — holds anything generated under a
     * version other than [version]. Consumed rows are history, not the pool, so a habit
     * whose only stale rows are consumed is not listed.
     */
    @Query(
        "SELECT DISTINCT v.habit_id FROM variations v JOIN habits h ON h.id = v.habit_id " +
        "WHERE h.active = 1 AND v.consumed_at IS NULL AND v.generation_version != :version"
    )
    suspend fun activeHabitIdsWithUnusedNotAtVersion(version: Int): List<Long>

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
}
