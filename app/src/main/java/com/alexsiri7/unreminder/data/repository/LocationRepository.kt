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

    suspend fun getByLabel(label: String): LocationEntity? = locationDao.getByLabel(label)

    suspend fun upsertLocation(label: String, lat: Double, lng: Double, radiusM: Float = 100f) {
        locationDao.deleteByLabel(label)
        locationDao.insert(LocationEntity(label = label, lat = lat, lng = lng, radiusM = radiusM))
    }
}
