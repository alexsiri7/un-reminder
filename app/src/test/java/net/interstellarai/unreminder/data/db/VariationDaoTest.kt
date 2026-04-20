package net.interstellarai.unreminder.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
            VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1"),
            VariationEntity(habitId = hId, text = "v2", promptFingerprint = "fp2")
        ))
        val unused = variationDao.getUnusedForHabit(hId, 50)
        assertEquals(2, unused.size)
    }

    @Test fun `markConsumed excludes row from getUnusedForHabit`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1")))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        variationDao.markConsumed(row.id, Instant.now().toEpochMilli())
        val unused = variationDao.getUnusedForHabit(hId, 50)
        assertTrue(unused.isEmpty())
    }

    @Test fun `markConsumed returns 1 on success and 0 for missing row`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1")))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        assertEquals(1, variationDao.markConsumed(row.id, Instant.now().toEpochMilli()))
        assertEquals(0, variationDao.markConsumed(999L, Instant.now().toEpochMilli()))
    }

    @Test fun `markConsumed returns 0 for already consumed row`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1")))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        assertEquals(1, variationDao.markConsumed(row.id, Instant.now().toEpochMilli()))
        assertEquals(0, variationDao.markConsumed(row.id, Instant.now().toEpochMilli()))
    }

    @Test fun `countUnused equals inserted minus consumed`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(
            VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1"),
            VariationEntity(habitId = hId, text = "v2", promptFingerprint = "fp2")
        ))
        val row = variationDao.getUnusedForHabit(hId, 50).first()
        variationDao.markConsumed(row.id, Instant.now().toEpochMilli())
        assertEquals(1, variationDao.countUnused(hId))
    }

    @Test fun `deleting habit cascades to its variations`() = runTest {
        val hId = insertHabit()
        variationDao.insert(listOf(VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1")))
        val habit = habitDao.getByIdOnce(hId)!!
        habitDao.delete(habit)
        assertEquals(0, variationDao.countUnused(hId))
    }

    @Test fun `duplicate habitId+promptFingerprint+text insert is ignored`() = runTest {
        val hId = insertHabit()
        val v = VariationEntity(habitId = hId, text = "v1", promptFingerprint = "fp1")
        variationDao.insert(listOf(v, v))
        assertEquals(1, variationDao.getUnusedForHabit(hId, 50).size)
    }

    @Test fun `getUnusedForHabit respects limit parameter`() = runTest {
        val hId = insertHabit()
        variationDao.insert((1..10).map { i ->
            VariationEntity(habitId = hId, text = "v$i", promptFingerprint = "fp$i")
        })
        val result = variationDao.getUnusedForHabit(hId, limit = 3)
        assertEquals(3, result.size)
    }
}
