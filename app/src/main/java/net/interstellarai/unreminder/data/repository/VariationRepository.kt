package net.interstellarai.unreminder.data.repository

import net.interstellarai.unreminder.data.db.VariationDao
import net.interstellarai.unreminder.data.db.VariationEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VariationRepository @Inject constructor(
    private val dao: VariationDao
) {
    suspend fun pickRandomUnused(habitId: Long): VariationEntity? {
        val unused = dao.getUnusedForHabit(habitId, 50)
        val picked = unused.randomOrNull() ?: return null
        dao.markConsumed(picked.id, Instant.now().toEpochMilli())
        return picked
    }

    suspend fun needsRefill(habitId: Long, threshold: Int = 5): Boolean =
        dao.countUnused(habitId) < threshold

    suspend fun insertAll(variants: List<VariationEntity>) = dao.insert(variants)

    suspend fun deleteForHabit(habitId: Long) = dao.deleteByHabit(habitId)
}
