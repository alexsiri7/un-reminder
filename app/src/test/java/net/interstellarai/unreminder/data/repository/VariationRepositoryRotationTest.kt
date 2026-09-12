package net.interstellarai.unreminder.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.AppDatabase
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.domain.model.VariantShape
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        repository = VariationRepository(db.variationDao())
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
            val unusedShapes = db.variationDao().getUnusedForHabit(habitId, 50).map { it.shape }.toSet()
            val picked = repository.pickRandomUnused(habitId)!!.shape
            if (unusedShapes.size > 1) assertNotEquals(previous, picked)
            previous = picked
        }
        assertEquals(0, db.variationDao().countUnused(habitId))
    }

    @Test fun `peek for the widget avoids the shape the last notification used`() = runTest {
        repository.insertAll(balancedPool(perShape = 3))
        val fired = repository.pickRandomUnused(habitId)!!.shape

        repeat(20) {
            assertNotEquals(fired, repository.peekUnusedVariation(habitId)!!.shape)
        }
    }
}
