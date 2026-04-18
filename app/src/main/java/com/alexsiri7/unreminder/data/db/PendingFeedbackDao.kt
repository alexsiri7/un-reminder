package com.alexsiri7.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingFeedbackDao {
    @Insert
    suspend fun insert(entity: PendingFeedbackEntity): Long

    @Query("SELECT * FROM pending_feedback ORDER BY queued_at ASC")
    suspend fun getAll(): List<PendingFeedbackEntity>

    @Query("DELETE FROM pending_feedback WHERE id = :id")
    suspend fun deleteById(id: Long)
}
