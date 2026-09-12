package net.interstellarai.unreminder.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import net.interstellarai.unreminder.domain.model.VariantShape
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class VariationDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var variationDao: VariationDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        habitDao = db.habitDao()
        variationDao = db.variationDao()
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun insertHabit(): Long = habitDao.insert(
        HabitEntity(name = "h")
    )

    @Test fun `insert then getUnusedForHabit returns inserted rows`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT),
            VariationEntity(habitId = hId, text = "v2", promptFingerprint = "fp2", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)
        ))
        val unused = variationDao.getUnusedForHabit(hId, 50)
        assertEquals(2, unused.size)
    }

    @Test fun `markConsumed excludes row from getUnusedForHabit`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        variationDao.markConsumed(row.id, Instant.now())
        val unused = variationDao.getUnusedForHabit(hId, 50)
        assertTrue(unused.isEmpty())
    }

    @Test fun `markConsumed returns 1 on success and 0 for missing row`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        assertEquals(1, variationDao.markConsumed(row.id, Instant.now()))
        assertEquals(0, variationDao.markConsumed(999L, Instant.now()))
    }

    @Test fun `markConsumed returns 0 for already consumed row`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        assertEquals(1, variationDao.markConsumed(row.id, Instant.now()))
        assertEquals(0, variationDao.markConsumed(row.id, Instant.now()))
    }

    @Test fun `countUnused equals inserted minus consumed`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT),
            VariationEntity(habitId = hId, text = "v2", promptFingerprint = "fp2", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)
        ))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        variationDao.markConsumed(row.id, Instant.now())
        assertEquals(1, variationDao.countUnused(hId))
    }

    @Test fun `deleting habit cascades to its variations`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)))
        val habit = habitDao.getByIdOnce(hId)!!
        habitDao.delete(habit)
        assertEquals(0, variationDao.countUnused(hId))
    }

    @Test fun `duplicate habitId+promptFingerprint+text insert is ignored`() = runTest {
        val hId = insertHabit()
        val v = VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)
        variationDao.insert(listOf(v, v))
        assertEquals(1, variationDao.getUnusedForHabit(hId, 50).size)
    }

    @Test fun `markConsumed writes a value readable as Instant by Room`() = runTest {
        val hId = insertHabit()
        val v = VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)
        variationDao.insert(listOf(v))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        val now = Instant.now()
        variationDao.markConsumed(row.id, now)

        val cursor = db.query("SELECT consumed_at FROM variations WHERE id = ${row.id}", emptyArray())
        cursor.moveToFirst()
        val storedValue = cursor.getLong(0)
        cursor.close()
        assertEquals(now.toEpochMilli(), storedValue)
    }

    @Test fun `getUnusedForHabit respects limit parameter`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1..10).map { i ->
            VariationEntity(habitId = hId, text = "v$i", promptFingerprint = "fp$i", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)
        })
        val result = variationDao.getUnusedForHabit(hId, limit = 3)
        assertEquals(3, result.size)
    }

    @Test fun `deleteByHabit removes only target habit variations`() = runTest {
        val h1 = insertHabit()
        val h2 = habitDao.insert(HabitEntity(name = "h2"))
        variationDao.insert(listOf(
            VariationEntity(habitId = h1, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT),
            VariationEntity(habitId = h2, text = "v2", promptFingerprint = "fp2", generatedAt = Instant.EPOCH, shape = VariantShape.STATEMENT)
        ))
        variationDao.deleteByHabit(h1)
        assertEquals(0, variationDao.countUnused(h1))
        assertEquals(1, variationDao.countUnused(h2))
    }

    private fun shaped(habitId: Long, shape: VariantShape?, index: Int) = VariationEntity(
        habitId = habitId, text = "$shape $index", promptFingerprint = "fp",
        generatedAt = Instant.EPOCH, shape = shape,
    )

    private suspend fun consumeOne(habitId: Long, shape: VariantShape) {
        val row = variationDao.getUnusedForHabit(habitId, 50).first { it.shape == shape }
        assertEquals(1, variationDao.markConsumed(row.id, Instant.now()))
    }

    @Test fun `getUnusedForHabit sorts rows sharing the last consumed shape after every other shape`() = runTest {
        val hId = insertHabit()
        variationDao.insert(VariantShape.entries.flatMap { shape -> (1..3).map { shaped(hId, shape, it) } })
        consumeOne(hId, VariantShape.QUESTION)

        repeat(20) {
            val ordered = variationDao.getUnusedForHabit(hId, 50)
            assertEquals(17, ordered.size)
            assertEquals(List(15) { false } + List(2) { true }, ordered.map { it.shape == VariantShape.QUESTION })
        }
    }

    @Test fun `getUnusedForHabit with limit 1 never repeats the last consumed shape while another is unused`() = runTest {
        val hId = insertHabit()
        variationDao.insert(VariantShape.entries.flatMap { shape -> (1..3).map { shaped(hId, shape, it) } })
        consumeOne(hId, VariantShape.TERSE)

        repeat(20) {
            assertNotEquals(VariantShape.TERSE, variationDao.getUnusedForHabit(hId, 1).single().shape)
        }
    }

    @Test fun `getUnusedForHabit follows the most recent consumption, not the first`() = runTest {
        val hId = insertHabit()
        variationDao.insert(VariantShape.entries.flatMap { shape -> (1..3).map { shaped(hId, shape, it) } })
        consumeOne(hId, VariantShape.QUESTION)
        consumeOne(hId, VariantShape.CHALLENGE)

        repeat(20) {
            val first = variationDao.getUnusedForHabit(hId, 1).single().shape
            assertNotEquals(VariantShape.CHALLENGE, first)
        }
        assertTrue(variationDao.getUnusedForHabit(hId, 50).takeWhile { it.shape != VariantShape.CHALLENGE }.any { it.shape == VariantShape.QUESTION })
    }

    @Test fun `getUnusedForHabit falls back to the last consumed shape when nothing else is unused`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1..3).map { shaped(hId, VariantShape.STATEMENT, it) })
        consumeOne(hId, VariantShape.STATEMENT)

        assertEquals(VariantShape.STATEMENT, variationDao.getUnusedForHabit(hId, 1).single().shape)
        assertEquals(2, variationDao.getUnusedForHabit(hId, 50).size)
    }

    @Test fun `getUnusedForHabit treats rows without a shape as a fresh shape`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1..3).map { shaped(hId, VariantShape.STATEMENT, it) } + (1..3).map { shaped(hId, null, it) })
        consumeOne(hId, VariantShape.STATEMENT)

        repeat(20) {
            assertNull(variationDao.getUnusedForHabit(hId, 1).single().shape)
        }
    }

    @Test fun `getUnusedForHabit ignores what other habits consumed`() = runTest {
        val h1 = insertHabit()
        val h2 = habitDao.insert(HabitEntity(name = "h2"))
        variationDao.insert((1..3).map { shaped(h1, VariantShape.TERSE, it) } + (1..3).map { shaped(h2, VariantShape.TERSE, it) } + (1..3).map { shaped(h2, VariantShape.QUESTION, it) })
        consumeOne(h2, VariantShape.TERSE)

        assertEquals(3, variationDao.getUnusedForHabit(h1, 50).size)
        repeat(20) {
            assertEquals(VariantShape.QUESTION, variationDao.getUnusedForHabit(h2, 1).single().shape)
        }
    }

    @Test fun `deleteConsumedByHabit keeps only the most recently consumed row`() = runTest {
        val hId = insertHabit()
        variationDao.insert(VariantShape.entries.flatMap { shape -> (1..2).map { shaped(hId, shape, it) } })
        consumeOne(hId, VariantShape.QUESTION)
        consumeOne(hId, VariantShape.TERSE)

        variationDao.deleteConsumedByHabit(hId)

        val consumed = variationDao.getRecentlyUsedFlow(hId, 50).first()
        assertEquals(listOf(VariantShape.TERSE), consumed.map { it.shape })
        assertEquals(10, variationDao.countUnused(hId))
    }

    @Test fun `deleteConsumedByHabit with nothing consumed leaves the pool alone`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1..3).map { shaped(hId, VariantShape.TERSE, it) })

        variationDao.deleteConsumedByHabit(hId)

        assertEquals(3, variationDao.countUnused(hId))
    }
}
