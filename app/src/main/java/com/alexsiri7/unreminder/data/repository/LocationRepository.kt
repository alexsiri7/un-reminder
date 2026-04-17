package com.alexsiri7.unreminder.data.repository

import com.alexsiri7.unreminder.data.db.LocationDao
import com.alexsiri7.unreminder.data.db.LocationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val locationDao: LocationDao
) {
    fun getAll(): Flow<List<LocationEntity>> = locationDao.getAll()

    suspend fun getAllList(): List<LocationEntity> = locationDao.getAllList()

    suspend fun getById(id: Long): LocationEntity? = locationDao.getById(id)

    suspend fun getByIds(ids: Set<Long>): List<LocationEntity> =
        locationDao.getByIds(ids.toList())

    suspend fun insert(name: String, lat: Double, lng: Double, radiusM: Float = 100f): Long =
        locationDao.insert(LocationEntity(name = name, lat = lat, lng = lng, radiusM = radiusM))

    suspend fun delete(location: LocationEntity) = locationDao.delete(location)
}
