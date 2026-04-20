package net.interstellarai.unreminder.data.repository

import android.util.Log
import net.interstellarai.unreminder.data.db.VariationDao
import net.interstellarai.unreminder.data.db.VariationEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VariationRepository @Inject constructor(
    private val dao: VariationDao
) {
    /**
     * Picks a random unconsumed variation for [habitId], marks it consumed, and returns it.
     * Returns null if the pool is empty or the row was deleted between fetch and mark —
     * callers should treat both as "nothing available; consider triggering a refill".
     * The returned entity is already marked consumed; do not call [VariationDao.markConsumed] again.
     */
    suspend fun pickRandomUnused(habitId: Long): VariationEntity? {
        val unused = dao.getUnusedForHabit(habitId, 50)
        val picked = unused.randomOrNull() ?: return null
        val now = Instant.now()
        val updated = dao.markConsumed(picked.id, now.toEpochMilli())
        if (updated == 0) {
            Log.w("VariationRepo", "markConsumed race: variation ${picked.id} was deleted before mark")
            return null
        }
        return picked.copy(consumedAt = now)
    }

    /**
     * Returns true when the unused variation pool for [habitId] has dropped below
     * [threshold] — the low-watermark that signals RefillWorker to top up the pool.
     * Default of 5 is 10% of the 50-item pool cap in [VariationDao.getUnusedForHabit].
     */
    suspend fun needsRefill(habitId: Long, threshold: Int = 5): Boolean =
        dao.countUnused(habitId) < threshold

    suspend fun insertAll(variants: List<VariationEntity>) = dao.insert(variants)

    suspend fun deleteForHabit(habitId: Long) = dao.deleteByHabit(habitId)
}
