package net.interstellarai.unreminder.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.domain.model.TriggerStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class TriggerDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var triggerDao: TriggerDao

    private val midnightMillis: Long = LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        habitDao = db.habitDao()
        triggerDao = db.triggerDao()
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun insertHabit(name: String): Long =
        habitDao.insert(HabitEntity(name = name, dailyLimit = 5))

    private suspend fun insertTrigger(habitId: Long, status: TriggerStatus, firedAtMillis: Long) {
        triggerDao.insert(
            TriggerEntity(
                habitId = habitId,
                scheduledAt = Instant.ofEpochMilli(midnightMillis),
                firedAt = Instant.ofEpochMilli(firedAtMillis),
                status = status
            )
        )
    }

    @Test
    fun `countNonScheduledSince excludes DISMISSED triggers`() = runTest {
        // With buggy SQL: COUNT=1 >= 1 → would exceed daily limit.
        // With fixed SQL: COUNT=0 → DISMISSED does not consume quota.
        val habitId = insertHabit("hDismissed")
        insertTrigger(habitId, TriggerStatus.DISMISSED, midnightMillis)

        val count = triggerDao.countNonScheduledSince(habitId, midnightMillis)

        assertEquals("DISMISSED must not count toward daily limit", 0, count)
    }

    @Test
    fun `countNonScheduledSince counts COMPLETED triggers`() = runTest {
        // Mutation check: same firedAt as the DISMISSED test — only status differs.
        val habitId = insertHabit("hCompleted")
        insertTrigger(habitId, TriggerStatus.COMPLETED, midnightMillis)

        val count = triggerDao.countNonScheduledSince(habitId, midnightMillis)

        assertEquals(1, count)
    }

    @Test
    fun `countNonScheduledSince counts FIRED triggers`() = runTest {
        val habitId = insertHabit("hFired")
        insertTrigger(habitId, TriggerStatus.FIRED, midnightMillis)

        val count = triggerDao.countNonScheduledSince(habitId, midnightMillis)

        assertEquals(1, count)
    }

    @Test
    fun `countNonScheduledSince counts COMPLETED and FIRED but not DISMISSED`() = runTest {
        val habitId = insertHabit("hMixed")
        insertTrigger(habitId, TriggerStatus.COMPLETED, midnightMillis)
        insertTrigger(habitId, TriggerStatus.FIRED, midnightMillis + 1)
        insertTrigger(habitId, TriggerStatus.DISMISSED, midnightMillis + 2)

        val count = triggerDao.countNonScheduledSince(habitId, midnightMillis)

        assertEquals("COMPLETED + FIRED = 2; DISMISSED must not count", 2, count)
    }

    @Test
    fun `countNonScheduledSince excludes triggers before the cutoff`() = runTest {
        val habitId = insertHabit("hOld")
        // 1ms before cutoff — must not be counted
        insertTrigger(habitId, TriggerStatus.COMPLETED, midnightMillis - 1)

        val count = triggerDao.countNonScheduledSince(habitId, midnightMillis)

        assertEquals("Triggers before cutoff must not count", 0, count)
    }
}
