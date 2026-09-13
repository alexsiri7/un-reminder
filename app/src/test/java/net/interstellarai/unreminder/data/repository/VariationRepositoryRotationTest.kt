package net.interstellarai.unreminder.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.AppDatabase
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.db.bit
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.VariantShape
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** Shape rotation end to end against a real database, the way the trigger pipeline and widget see it. */
@RunWith(RobolectricTestRunner::class)
class VariationRepositoryRotationTest {

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

    private fun balancedPool(perShape: Int) = VariantShape.entries.flatMap { shape ->
        (1..perShape).map { i ->
            VariationEntity(
                habitId = habitId, text = "$shape $i", promptFingerprint = "fp",
                generatedAt = Instant.EPOCH, shape = shape,
            )
        }
    }

    @Test fun `consecutive picks never share a shape while another shape is still unused`() = runTest {
        repository.insertAll(balancedPool(perShape = 5))

        var previous: VariantShape? = null
        repeat(30) {
            val unusedShapes = db.variationDao().getUnusedForHabit(habitId, ActivityMode.SITTING.bit, 50).map { it.shape }.toSet()
            val picked = repository.pickRandomUnused(habitId, ActivityMode.SITTING)!!.shape
            if (unusedShapes.size > 1) assertNotEquals(previous, picked)
            previous = picked
        }
        assertEquals(0, db.variationDao().countUnused(habitId))
    }

    @Test fun `peek for the widget avoids the shape the last notification used`() = runTest {
        repository.insertAll(balancedPool(perShape = 3))
        val fired = repository.pickRandomUnused(habitId, ActivityMode.SITTING)!!.shape

        repeat(20) {
            assertNotEquals(fired, repository.peekUnusedVariation(habitId, ActivityMode.SITTING)!!.shape)
        }
    }

    @Test fun `the first pick after a refill still avoids the shape that fired last`() = runTest {
        repository.insertAll(balancedPool(perShape = 1))
        val fired = repository.pickRandomUnused(habitId, ActivityMode.SITTING)!!.shape

        repository.refill(habitId, VariationEntity.UNVERSIONED, balancedPool(perShape = 3).map { it.copy(promptFingerprint = "fp2") })

        repeat(20) {
            assertNotEquals(fired, repository.peekUnusedVariation(habitId, ActivityMode.SITTING)!!.shape)
        }
        assertNotEquals(fired, repository.pickRandomUnused(habitId, ActivityMode.SITTING)!!.shape)
    }

    private fun modePool(modes: Set<ActivityMode>, count: Int) = (1..count).map { i ->
        VariationEntity(
            habitId = habitId, text = "${modes.joinToString("+").ifEmpty { "neutral" }} $i", promptFingerprint = "fp",
            generatedAt = Instant.EPOCH, shape = VariantShape.entries[i % VariantShape.entries.size], modes = modes,
        )
    }

    @Test fun `while walking, walking variants fire first and neutral ones only once they run out`() = runTest {
        repository.insertAll(modePool(setOf(ActivityMode.WALKING), 4) + modePool(emptySet(), 4) + modePool(setOf(ActivityMode.SITTING), 4))

        repeat(4) { assertEquals(setOf(ActivityMode.WALKING), repository.pickRandomUnused(habitId, ActivityMode.WALKING)!!.modes) }
        repeat(4) { assertEquals(emptySet<ActivityMode>(), repository.pickRandomUnused(habitId, ActivityMode.WALKING)!!.modes) }
        repeat(4) { assertEquals(setOf(ActivityMode.SITTING), repository.pickRandomUnused(habitId, ActivityMode.WALKING)!!.modes) }
        assertNull(repository.pickRandomUnused(habitId, ActivityMode.WALKING))
    }

    @Test fun `a habit with no variant for the current mode still fires, neutral first`() = runTest {
        repository.insertAll(modePool(setOf(ActivityMode.SITTING), 3) + modePool(emptySet(), 2))

        assertEquals(emptySet<ActivityMode>(), repository.peekUnusedVariation(habitId, ActivityMode.TRANSPORT)!!.modes)
        assertEquals(emptySet<ActivityMode>(), repository.pickRandomUnused(habitId, ActivityMode.TRANSPORT)!!.modes)
        assertEquals(emptySet<ActivityMode>(), repository.pickRandomUnused(habitId, ActivityMode.WALKING)!!.modes)
        assertEquals(setOf(ActivityMode.SITTING), repository.pickRandomUnused(habitId, ActivityMode.WALKING)!!.modes)
    }
}
