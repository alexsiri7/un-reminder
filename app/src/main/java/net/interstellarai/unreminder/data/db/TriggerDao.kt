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

    @Query("SELECT * FROM triggers ORDER BY scheduled_at DESC LIMIT :limit")
    fun getRecentTriggers(limit: Int = 20): Flow<List<TriggerEntity>>

    @Query("SELECT * FROM triggers WHERE status = 'SCHEDULED' AND scheduled_at >= :fromMillis AND scheduled_at < :toMillis")
    suspend fun getScheduledForRange(fromMillis: Long, toMillis: Long): List<TriggerEntity>

    @Query("SELECT * FROM triggers WHERE status = 'SCHEDULED'")
    suspend fun getAllScheduled(): List<TriggerEntity>

    @Query("SELECT * FROM triggers WHERE id = :id")
    suspend fun getById(id: Long): TriggerEntity?

    @Query("UPDATE triggers SET status = :status, fired_at = :firedAt, habit_id = :habitId, generated_prompt = :prompt WHERE id = :id")
    suspend fun updateFired(id: Long, status: String, firedAt: Long, habitId: Long, prompt: String)

    @Query("UPDATE triggers SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM triggers WHERE status = 'SCHEDULED'")
    suspend fun deleteAllScheduled()

    @Query("SELECT fired_at FROM triggers WHERE habit_id = :habitId AND fired_at IS NOT NULL ORDER BY fired_at DESC LIMIT 1")
    suspend fun getLastFiredForHabit(habitId: Long): Long?

    @Query("SELECT * FROM triggers WHERE habit_id = :habitId AND fired_at IS NOT NULL ORDER BY fired_at DESC LIMIT :limit")
    suspend fun getLastNForHabit(habitId: Long, limit: Int): List<TriggerEntity>

    @Query("""
        SELECT COUNT(*) FROM triggers
        WHERE habit_id = :habitId
        AND status IN ('COMPLETED', 'COMPLETED_FULL', 'COMPLETED_LOW_FLOOR')
        AND fired_at >= :sinceMillis
    """)
    suspend fun countCompletionsSince(habitId: Long, sinceMillis: Long): Int
}
