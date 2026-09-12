package net.interstellarai.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TriggerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trigger: TriggerEntity): Long

    @Update
    suspend fun update(trigger: TriggerEntity)

    // != 'SCHEDULED' (not IN whitelist) so legacy DB rows with status 'COMPLETED_FULL' or
    // 'COMPLETED_LOW_FLOOR' (see Converters.toTriggerStatus) also pass through.
    @Query("SELECT * FROM triggers WHERE status != 'SCHEDULED' ORDER BY scheduled_at DESC LIMIT :limit")
    fun getRecentTriggers(limit: Int = 20): Flow<List<TriggerEntity>>

    @Query("SELECT * FROM triggers WHERE status = 'SCHEDULED' AND scheduled_at >= :fromMillis AND scheduled_at < :toMillis")
    suspend fun getScheduledForRange(fromMillis: Long, toMillis: Long): List<TriggerEntity>

    @Query("SELECT * FROM triggers WHERE status = 'SCHEDULED'")
    suspend fun getAllScheduled(): List<TriggerEntity>

    /** Ids of triggers whose notification is posted and still unanswered. */
    @Query("SELECT id FROM triggers WHERE status = 'FIRED'")
    suspend fun getFiredIds(): List<Long>

    @Query("SELECT * FROM triggers WHERE id = :id")
    suspend fun getById(id: Long): TriggerEntity?

    @Query("UPDATE triggers SET status = :status, fired_at = :firedAt, habit_id = :habitId, generated_prompt = :prompt WHERE id = :id")
    suspend fun updateFired(id: Long, status: String, firedAt: Long, habitId: Long, prompt: String)

    @Query("UPDATE triggers SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    /**
     * Records EXPIRED only while the trigger is still unanswered. A real outcome written
     * concurrently by another path therefore wins and is never overwritten.
     */
    @Query("UPDATE triggers SET status = 'EXPIRED' WHERE id = :id AND status = 'FIRED'")
    suspend fun markExpiredIfUnanswered(id: Long)

    @Query("DELETE FROM triggers WHERE status = 'SCHEDULED' AND scheduled_at < :cutoffMillis")
    suspend fun deleteScheduledOlderThan(cutoffMillis: Long)

    @Query("SELECT fired_at FROM triggers WHERE habit_id = :habitId AND fired_at IS NOT NULL ORDER BY fired_at DESC LIMIT 1")
    suspend fun getLastFiredForHabit(habitId: Long): Long?

    @Query("SELECT * FROM triggers WHERE habit_id = :habitId AND fired_at IS NOT NULL ORDER BY fired_at DESC LIMIT :limit")
    suspend fun getLastNForHabit(habitId: Long, limit: Int): List<TriggerEntity>

    @Query("""
        SELECT * FROM triggers
        WHERE habit_id = :habitId
          AND fired_at IS NOT NULL
          AND fired_at > :sinceMillis
          AND (status = 'COMPLETED' OR status = 'COMPLETED_FULL' OR status = 'COMPLETED_LOW_FLOOR')
        ORDER BY fired_at DESC
    """)
    suspend fun getCompletionsSince(habitId: Long, sinceMillis: Long): List<TriggerEntity>

    /**
     * Returns max fired_at for triggers that were not completed — DISMISSED, still-FIRED,
     * EXPIRED, or deferred with LATER (used for per-habit cooldown check). A superseded or
     * deferred notification was still a nudge that happened, so neither may shorten the
     * cooldown it started.
     */
    @Query("""
        SELECT MAX(fired_at) FROM triggers
        WHERE habit_id = :habitId
          AND fired_at IS NOT NULL
          AND (status = 'DISMISSED' OR status = 'FIRED' OR status = 'EXPIRED' OR status = 'LATER')
    """)
    suspend fun getLastFiredOrDismissedForHabit(habitId: Long): Long?

    /** Counts COMPLETED (exact status) triggers since a cutoff (used for completed-today check). */
    @Query("""
        SELECT COUNT(*) FROM triggers
        WHERE habit_id = :habitId
          AND fired_at IS NOT NULL
          AND status = 'COMPLETED'
          AND fired_at > :sinceMillis
    """)
    suspend fun countCompletedSince(habitId: Long, sinceMillis: Long): Int

    /**
     * Counts COMPLETED triggers (including legacy variants) since a cutoff for the per-habit
     * daily-limit check; mirrors the `< h.daily_limit` clause in HabitDao.getEligibleHabits.
     * Only completed actions count toward the daily limit; dismissed or expired notifications do not.
     */
    @Query("""
        SELECT COUNT(*) FROM triggers
        WHERE habit_id = :habitId
          AND fired_at IS NOT NULL
          AND fired_at >= :sinceMillis
          AND (status = 'COMPLETED' OR status = 'COMPLETED_FULL' OR status = 'COMPLETED_LOW_FLOOR')
    """)
    suspend fun countDailyCompletionsSince(habitId: Long, sinceMillis: Long): Int

    /**
     * Distinct local calendar days on which anything was completed. [offsetMillis] is the caller's
     * zone offset; a single offset is applied to every row, so history that crosses a DST boundary
     * can bucket an hour off. Legacy completed-status variants count (see Converters.toTriggerStatus).
     */
    @Query("""
        SELECT COUNT(DISTINCT date((fired_at + :offsetMillis) / 1000, 'unixepoch')) FROM triggers
        WHERE fired_at IS NOT NULL
          AND (status = 'COMPLETED' OR status = 'COMPLETED_FULL' OR status = 'COMPLETED_LOW_FLOOR')
    """)
    fun daysWithAnyCompletion(offsetMillis: Long): Flow<Int>

    /** Distinct local calendar days on which this habit was completed. */
    @Query("""
        SELECT COUNT(DISTINCT date((fired_at + :offsetMillis) / 1000, 'unixepoch')) FROM triggers
        WHERE habit_id = :habitId
          AND fired_at IS NOT NULL
          AND (status = 'COMPLETED' OR status = 'COMPLETED_FULL' OR status = 'COMPLETED_LOW_FLOOR')
    """)
    fun daysWithHabitCompletion(habitId: Long, offsetMillis: Long): Flow<Int>

    /** Every completion of this habit, including several on the same day. */
    @Query("""
        SELECT COUNT(*) FROM triggers
        WHERE habit_id = :habitId
          AND (status = 'COMPLETED' OR status = 'COMPLETED_FULL' OR status = 'COMPLETED_LOW_FLOOR')
    """)
    fun totalCompletionsForHabit(habitId: Long): Flow<Int>

    /** Completions of any habit since a cutoff; backs the "completed anything today" flow. */
    @Query("""
        SELECT COUNT(*) FROM triggers
        WHERE fired_at IS NOT NULL
          AND fired_at >= :sinceMillis
          AND (status = 'COMPLETED' OR status = 'COMPLETED_FULL' OR status = 'COMPLETED_LOW_FLOOR')
    """)
    fun countAnyCompletionsSince(sinceMillis: Long): Flow<Int>
}
