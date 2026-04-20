package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.HabitLevelDescriptionDao
import net.interstellarai.unreminder.data.db.HabitLevelDescriptionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitLevelDescriptionRepository @Inject constructor(
    private val dao: HabitLevelDescriptionDao
) {
    suspend fun getDescriptionsForHabit(habitId: Long): List<HabitLevelDescriptionEntity> =
        dao.getForHabit(habitId)

    suspend fun getDescriptionForLevel(habitId: Long, level: Int): String =
        dao.getForHabitAndLevel(habitId, level)?.description ?: ""

    suspend fun upsertAll(entries: List<HabitLevelDescriptionEntity>) =
        dao.upsertAll(entries)

    suspend fun replaceForHabit(habitId: Long, descriptions: List<String>) {
        val entries = descriptions.mapIndexed { level, text ->
            HabitLevelDescriptionEntity(habitId = habitId, level = level, description = text)
        }
        dao.deleteAndInsertForHabit(habitId, entries)
    }
}
