package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.WindowDao
import net.interstellarai.unreminder.data.db.WindowEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WindowRepository @Inject constructor(
    private val windowDao: WindowDao
) {
    fun getAll(): Flow<List<WindowEntity>> = windowDao.getAll()

    fun getById(id: Long): Flow<WindowEntity?> = windowDao.getById(id)

    suspend fun insert(window: WindowEntity): Long = windowDao.insert(window)

    suspend fun update(window: WindowEntity) = windowDao.update(window)

    suspend fun delete(window: WindowEntity) = windowDao.delete(window)

    suspend fun getActiveWindows(): List<WindowEntity> = windowDao.getAllActive()
}
