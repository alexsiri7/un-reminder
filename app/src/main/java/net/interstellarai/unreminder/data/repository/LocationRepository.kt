package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.LocationDao
import net.interstellarai.unreminder.data.db.LocationEntity
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

    suspend fun getByName(name: String): LocationEntity? = locationDao.getByName(name)

    suspend fun upsertLocation(name: String, lat: Double, lng: Double, radiusM: Float = 100f): Long {
        locationDao.deleteByName(name)
        return locationDao.insert(LocationEntity(name = name, lat = lat, lng = lng, radiusM = radiusM))
    }
}
