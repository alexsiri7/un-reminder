package net.interstellarai.unreminder.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
        HabitEntity(name = "h", fullDescription = "f", lowFloorDescription = "l")
    )

    @Test fun `insert then getUnusedForHabit returns inserted rows`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH),
            VariationEntity(habitId = hId, text = "v2", promptFingerprint = "fp2", generatedAt = Instant.EPOCH)
        ))
        val unused = variationDao.getUnusedForHabit(hId, 50)
        assertEquals(2, unused.size)
    }

    @Test fun `markConsumed excludes row from getUnusedForHabit`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH)))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        variationDao.markConsumed(row.id, Instant.now())
        val unused = variationDao.getUnusedForHabit(hId, 50)
        assertTrue(unused.isEmpty())
    }

    @Test fun `markConsumed returns 1 on success and 0 for missing row`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH)))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        assertEquals(1, variationDao.markConsumed(row.id, Instant.now()))
        assertEquals(0, variationDao.markConsumed(999L, Instant.now()))
    }

    @Test fun `markConsumed returns 0 for already consumed row`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH)))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        assertEquals(1, variationDao.markConsumed(row.id, Instant.now()))
        assertEquals(0, variationDao.markConsumed(row.id, Instant.now()))
    }

    @Test fun `countUnused equals inserted minus consumed`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH),
            VariationEntity(habitId = hId, text = "v2", promptFingerprint = "fp2", generatedAt = Instant.EPOCH)
        ))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        variationDao.markConsumed(row.id, Instant.now())
        assertEquals(1, variationDao.countUnused(hId))
    }

    @Test fun `deleting habit cascades to its variations`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH)))
        val habit = habitDao.getByIdOnce(hId)!!
        habitDao.delete(habit)
        assertEquals(0, variationDao.countUnused(hId))
    }

    @Test fun `duplicate habitId+promptFingerprint+text insert is ignored`() = runTest {
        val hId = insertHabit()
        val v = VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH)
        variationDao.insert(listOf(v, v))
        assertEquals(1, variationDao.getUnusedForHabit(hId, 50).size)
    }

    @Test fun `markConsumed writes a value readable as Instant by Room`() = runTest {
        val hId = insertHabit()
        val v = VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH)
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
            VariationEntity(habitId = hId, text = "v$i", promptFingerprint = "fp$i", generatedAt = Instant.EPOCH)
        })
        val result = variationDao.getUnusedForHabit(hId, limit = 3)
        assertEquals(3, result.size)
    }

    @Test fun `deleteByHabit removes only target habit variations`() = runTest {
        val h1 = insertHabit()
        val h2 = habitDao.insert(HabitEntity(name = "h2", fullDescription = "f2", lowFloorDescription = "l2"))
        variationDao.insert(listOf(
            VariationEntity(habitId = h1, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH),
            VariationEntity(habitId = h2, text = "v2", promptFingerprint = "fp2", generatedAt = Instant.EPOCH)
        ))
        variationDao.deleteByHabit(h1)
        assertEquals(0, variationDao.countUnused(h1))
        assertEquals(1, variationDao.countUnused(h2))
    }

    @Test fun `getUnusedFlow emits unused variations ordered newest first`() = runTest {
        val hId = insertHabit()
        val older = Instant.EPOCH
        val newer = Instant.EPOCH.plusSeconds(3600)
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "old", promptFingerprint = "fp1", generatedAt = older),
            VariationEntity(habitId = hId, text = "new", promptFingerprint = "fp2", generatedAt = newer),
        ))
        val result = variationDao.getUnusedFlow(hId).first()
        assertEquals(2, result.size)
        assertEquals("new", result[0].text)
        assertEquals("old", result[1].text)
    }

    @Test fun `getUnusedFlow excludes consumed variations`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "unused", promptFingerprint = "fp1", generatedAt = Instant.EPOCH),
            VariationEntity(habitId = hId, text = "used", promptFingerprint = "fp2", generatedAt = Instant.EPOCH),
        ))
        val usedRow = variationDao.getUnusedForHabit(hId, 50).first { it.text == "used" }
        variationDao.markConsumed(usedRow.id, Instant.now())
        val result = variationDao.getUnusedFlow(hId).first()
        assertEquals(1, result.size)
        assertEquals("unused", result[0].text)
    }

    @Test fun `getRecentlyUsedFlow returns consumed rows ordered by consumed_at DESC with limit`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1..5).map { i ->
            VariationEntity(habitId = hId, text = "v$i", promptFingerprint = "fp$i", generatedAt = Instant.EPOCH)
        })
        val rows = variationDao.getUnusedForHabit(hId, 50)
        rows.forEachIndexed { idx, row ->
            variationDao.markConsumed(row.id, Instant.EPOCH.plusSeconds(idx.toLong()))
        }
        val result = variationDao.getRecentlyUsedFlow(hId, limit = 3).first()
        assertEquals(3, result.size)
        assertTrue(result[0].consumedAt!! >= result[1].consumedAt!!)
    }

    @Test fun `deleteById removes only the targeted variation`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "target", promptFingerprint = "fp1", generatedAt = Instant.EPOCH),
            VariationEntity(habitId = hId, text = "keeper", promptFingerprint = "fp2", generatedAt = Instant.EPOCH),
        ))
        val target = variationDao.getUnusedForHabit(hId, 50).first { it.text == "target" }
        variationDao.deleteById(target.id)
        val remaining = variationDao.getUnusedFlow(hId).first()
        assertEquals(1, remaining.size)
        assertEquals("keeper", remaining[0].text)
    }

    @Test fun `countTotalFlow includes both used and unused`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH),
            VariationEntity(habitId = hId, text = "v2", promptFingerprint = "fp2", generatedAt = Instant.EPOCH),
        ))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        variationDao.markConsumed(row.id, Instant.now())
        val count = variationDao.countTotalFlow(hId).first()
        assertEquals(2, count)
    }

    @Test fun `countConsumedSinceFlow counts variations consumed at or after dayStart`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1", generatedAt = Instant.EPOCH),
            VariationEntity(habitId = hId, text = "v2", promptFingerprint = "fp2", generatedAt = Instant.EPOCH),
            VariationEntity(habitId = hId, text = "v3", promptFingerprint = "fp3", generatedAt = Instant.EPOCH),
        ))
        val rows = variationDao.getUnusedForHabit(hId, 50)
        val dayStart = Instant.EPOCH.plusSeconds(1000)
        variationDao.markConsumed(rows[0].id, Instant.EPOCH.plusSeconds(500))   // before dayStart
        variationDao.markConsumed(rows[1].id, Instant.EPOCH.plusSeconds(1000))  // at dayStart
        variationDao.markConsumed(rows[2].id, Instant.EPOCH.plusSeconds(2000))  // after dayStart
        val count = variationDao.countConsumedSinceFlow(hId, dayStart).first()
        assertEquals(2, count) // rows[1] and rows[2]
    }
}
