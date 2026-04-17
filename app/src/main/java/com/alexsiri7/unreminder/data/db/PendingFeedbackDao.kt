package com.alexsiri7.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingFeedbackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: PendingFeedbackEntity): Long

    @Query("SELECT * FROM pending_feedback WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingFeedbackEntity?

    @Query("SELECT * FROM pending_feedback ORDER BY created_at ASC")
    suspend fun getAll(): List<PendingFeedbackEntity>

    @Query("DELETE FROM pending_feedback WHERE id = :id")
    suspend fun deleteById(id: Long)
}
