package com.alexsiri7.unreminder.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WindowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(window: WindowEntity): Long

    @Update
    suspend fun update(window: WindowEntity)

    @Delete
    suspend fun delete(window: WindowEntity)

    @Query("SELECT * FROM windows WHERE id = :id")
    fun getById(id: Long): Flow<WindowEntity?>

    @Query("SELECT * FROM windows ORDER BY start_time ASC")
    fun getAll(): Flow<List<WindowEntity>>

    @Query("SELECT * FROM windows WHERE active = 1")
    suspend fun getAllActive(): List<WindowEntity>
}
