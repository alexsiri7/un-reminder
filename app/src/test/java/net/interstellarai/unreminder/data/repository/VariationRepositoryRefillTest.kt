package net.interstellarai.unreminder.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.AppDatabase
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.VariantShape
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** A version bump swaps a habit's pool against a real database: no gap, no mixed vintage. */
@RunWith(RobolectricTestRunner::class)
class VariationRepositoryRefillTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: VariationRepository
    private var habitId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = VariationRepository(db.variationDao(), db)
        habitId = db.habitDao().insert(HabitEntity(name = "h"))
    }

    @After
    fun tearDown() { db.close() }

    private fun pool(version: Int, count: Int, fingerprint: String = "fp") = (1..count).map { i ->
        VariationEntity(
            habitId = habitId, text = "v$version #$i", promptFingerprint = fingerprint,
            generatedAt = Instant.EPOCH, shape = VariantShape.entries[i % VariantShape.entries.size],
            generationVersion = version,
        )
    }

    private suspend fun unused() = db.variationDao().getUnusedFlow(habitId).first()

    @Test fun `a refill at a new version replaces the whole unconsumed pool with the batch`() = runTest {
        repository.insertAll(pool(version = 1, count = 10))

        repository.refill(habitId, 2, pool(version = 2, count = 8))

        val rows = unused()
        assertEquals(8, rows.size)
        assertEquals(setOf(2), rows.map { it.generationVersion }.toSet())
        assertEquals((1..8).map { "v2 #$it" }.toSet(), rows.map { it.text }.toSet())
    }

    @Test fun `an empty batch at a new version leaves the existing pool alone`() = runTest {
        repository.insertAll(pool(version = 1, count = 10))

        repository.refill(habitId, 2, emptyList())

        val rows = unused()
        assertEquals(10, rows.size)
        assertEquals(setOf(1), rows.map { it.generationVersion }.toSet())
    }

    @Test fun `the last consumed row survives a version swap and still steers shape rotation`() = runTest {
        repository.insertAll(pool(version = 1, count = 1))
        val fired = repository.pickRandomUnused(habitId, ActivityMode.SITTING)!!

        repository.refill(habitId, 2, pool(version = 2, count = 18))

        assertEquals(listOf(fired.id), db.variationDao().getRecentlyUsedFlow(habitId, 50).first().map { it.id })
        repeat(20) {
            assertNotEquals(fired.shape, repository.peekUnusedVariation(habitId, ActivityMode.SITTING)!!.shape)
        }
    }

    @Test fun `a replace refill at the same version swaps the whole unconsumed pool for the batch`() = runTest {
        repository.insertAll(pool(version = 2, count = 10))

        repository.refill(habitId, 2, pool(version = 2, count = 18).drop(10), replace = true)

        val rows = unused()
        assertEquals(8, rows.size)
        assertEquals((11..18).map { "v2 #$it" }.toSet(), rows.map { it.text }.toSet())
    }

    @Test fun `a replace refill sweeps unversioned and other-version rows too`() = runTest {
        repository.insertAll(pool(version = VariationEntity.UNVERSIONED, count = 3))
        repository.insertAll(pool(version = 1, count = 4))
        repository.insertAll(pool(version = 2, count = 5))

        repository.refill(habitId, 2, pool(version = 2, count = 7).drop(5), replace = true)

        val rows = unused()
        assertEquals(2, rows.size)
        assertEquals(setOf("v2 #6", "v2 #7"), rows.map { it.text }.toSet())
    }

    @Test fun `an empty batch in replace mode leaves the existing pool alone`() = runTest {
        repository.insertAll(pool(version = 2, count = 10))

        repository.refill(habitId, 2, emptyList(), replace = true)

        assertEquals(10, unused().size)
    }

    @Test fun `the last consumed row survives a replace refill and still steers shape rotation`() = runTest {
        repository.insertAll(pool(version = 2, count = 1))
        val fired = repository.pickRandomUnused(habitId, ActivityMode.SITTING)!!

        repository.refill(habitId, 2, pool(version = 2, count = 19).drop(1), replace = true)

        assertEquals(listOf(fired.id), db.variationDao().getRecentlyUsedFlow(habitId, 50).first().map { it.id })
        repeat(20) {
            assertNotEquals(fired.shape, repository.peekUnusedVariation(habitId, ActivityMode.SITTING)!!.shape)
        }
    }

    @Test fun `a refill at the same version is additive`() = runTest {
        repository.insertAll(pool(version = 2, count = 5))

        repository.refill(habitId, 2, pool(version = 2, count = 10).drop(5))

        assertEquals(10, unused().size)
    }

    @Test fun `a refill from an unversioned worker leaves versioned rows alone`() = runTest {
        repository.insertAll(pool(version = 1, count = 5))

        repository.refill(habitId, VariationEntity.UNVERSIONED, pool(version = VariationEntity.UNVERSIONED, count = 3))

        assertEquals(8, unused().size)
    }

    @Test fun `a new-version row that repeats an old row's text and fingerprint still lands`() = runTest {
        repository.insertAll(pool(version = 1, count = 3))
        val repeated = pool(version = 1, count = 3).map { it.copy(generationVersion = 2) }

        repository.refill(habitId, 2, repeated)

        val rows = unused()
        assertEquals(3, rows.size)
        assertEquals(setOf(2), rows.map { it.generationVersion }.toSet())
    }

    @Test fun `rows generated before versions existed are swept by the first versioned refill`() = runTest {
        repository.insertAll((1..4).map { i ->
            VariationEntity(habitId = habitId, text = "legacy $i", promptFingerprint = "fp", generatedAt = Instant.EPOCH, shape = null)
        })

        repository.refill(habitId, 1, pool(version = 1, count = 2))

        val rows = unused()
        assertEquals(2, rows.size)
        assertEquals(setOf(1), rows.map { it.generationVersion }.toSet())
    }
}
