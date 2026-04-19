package net.interstellarai.unreminder.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity): Long

    @Update
    suspend fun update(location: LocationEntity)

    @Delete
    suspend fun delete(location: LocationEntity)

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getById(id: Long): LocationEntity?

    @Query("SELECT * FROM locations WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<LocationEntity>

    @Query("SELECT * FROM locations")
    fun getAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations")
    suspend fun getAllList(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): LocationEntity?

    @Query("DELETE FROM locations WHERE name = :name")
    suspend fun deleteByName(name: String)
}
