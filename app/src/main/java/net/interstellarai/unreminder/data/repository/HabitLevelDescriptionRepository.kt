package net.interstellarai.unreminder.data.repository

import androidx.room.withTransaction
import net.interstellarai.unreminder.data.db.AppDatabase
import net.interstellarai.unreminder.data.db.HabitLevelDescriptionDao
import net.interstellarai.unreminder.data.db.HabitLevelDescriptionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitLevelDescriptionRepository @Inject constructor(
    private val dao: HabitLevelDescriptionDao,
    private val db: AppDatabase
) {
    suspend fun getDescriptionsForHabit(habitId: Long): List<HabitLevelDescriptionEntity> =
        dao.getForHabit(habitId)

    suspend fun getDescriptionForLevel(habitId: Long, level: Int): String? =
        dao.getForLevel(habitId, level)?.description

    suspend fun setDescriptions(habitId: Long, descriptions: List<String>) {
        db.withTransaction {
            dao.deleteByHabit(habitId)
            dao.insertAll(
                descriptions.mapIndexedNotNull { i, desc ->
                    if (desc.isBlank()) null
                    else HabitLevelDescriptionEntity(habitId = habitId, level = i, description = desc)
                }
            )
        }
    }
}
