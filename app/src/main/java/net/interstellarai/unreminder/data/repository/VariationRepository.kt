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
    companion object {
        const val POOL_SIZE = 50
        const val REFILL_THRESHOLD = 5
    }

    /**
     * Picks a random unconsumed variation for [habitId], marks it consumed, and returns it.
     * Returns null only when the pool is truly empty (no unconsumed rows remain) —
     * callers should treat null as "nothing available; consider triggering a refill".
     * The returned entity is already marked consumed; do not call [VariationDao.markConsumed] again.
     */
    suspend fun pickRandomUnused(habitId: Long): VariationEntity? {
        val unused = dao.getUnusedForHabit(habitId, POOL_SIZE).shuffled()
        val now = Instant.now()
        for (candidate in unused) {
            val updated = dao.markConsumed(candidate.id, now.toEpochMilli())
            if (updated == 1) {
                return candidate.copy(consumedAt = now)
            }
            Log.w("VariationRepo", "markConsumed race: variation ${candidate.id} already consumed or deleted")
        }
        return null
    }

    /**
     * Returns true when the unused variation pool for [habitId] has dropped below
     * [threshold] — the low-watermark that signals RefillWorker to top up the pool.
     * Default of [REFILL_THRESHOLD] is 10% of the [POOL_SIZE]-item pool cap.
     */
    suspend fun needsRefill(habitId: Long, threshold: Int = REFILL_THRESHOLD): Boolean =
        dao.countUnused(habitId) < threshold

    suspend fun insertAll(variants: List<VariationEntity>) = dao.insert(variants)

    suspend fun deleteForHabit(habitId: Long) = dao.deleteByHabit(habitId)
}
