package com.alexsiri7.unreminder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity): Long

    @Query("SELECT * FROM locations WHERE label = :label LIMIT 1")
    suspend fun getByLabel(label: String): LocationEntity?

    @Query("SELECT * FROM locations")
    fun getAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations")
    suspend fun getAllList(): List<LocationEntity>

    @Query("DELETE FROM locations WHERE label = :label")
    suspend fun deleteByLabel(label: String)
}
