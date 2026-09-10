package net.interstellarai.unreminder.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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

    private suspend fun insertTrigger(habitId: Long?, status: TriggerStatus, firedAtMillis: Long) {
        triggerDao.insert(
            TriggerEntity(
                habitId = habitId,
                scheduledAt = Instant.ofEpochMilli(midnightMillis),
                firedAt = Instant.ofEpochMilli(firedAtMillis),
                status = status
            )
        )
    }

    // Growth counters (#292). Absolute epoch millis and a hardcoded offset keep these independent
    // of the machine's zone: at UTC-8 a local day starts at 08:00 UTC, so every case below is
    // bucketed differently by a correct implementation than by plain UTC bucketing.
    private val offsetMillisUtcMinus8: Long = -8 * 60 * 60 * 1000L

    private fun utcMillis(isoInstant: String): Long = Instant.parse(isoInstant).toEpochMilli()

    @Test
    fun `countDailyCompletionsSince excludes DISMISSED triggers`() = runTest {
        // With buggy SQL: COUNT=1 >= 1 → would exceed daily limit.
        // With fixed SQL: COUNT=0 → DISMISSED does not consume quota.
        val habitId = insertHabit("hDismissed")
        insertTrigger(habitId, TriggerStatus.DISMISSED, midnightMillis)

        val count = triggerDao.countDailyCompletionsSince(habitId, midnightMillis)

        assertEquals("DISMISSED must not count toward daily limit", 0, count)
    }

    @Test
    fun `countDailyCompletionsSince counts COMPLETED triggers`() = runTest {
        // Mutation check: same firedAt as the DISMISSED test — only status differs.
        val habitId = insertHabit("hCompleted")
        insertTrigger(habitId, TriggerStatus.COMPLETED, midnightMillis)

        val count = triggerDao.countDailyCompletionsSince(habitId, midnightMillis)

        assertEquals(1, count)
    }

    @Test
    fun `countDailyCompletionsSince does not count FIRED triggers`() = runTest {
        val habitId = insertHabit("hFired")
        insertTrigger(habitId, TriggerStatus.FIRED, midnightMillis)

        val count = triggerDao.countDailyCompletionsSince(habitId, midnightMillis)

        assertEquals("FIRED (timed-out) must not consume daily quota", 0, count)
    }

    @Test
    fun `countDailyCompletionsSince counts only COMPLETED not FIRED or DISMISSED`() = runTest {
        val habitId = insertHabit("hMixed")
        insertTrigger(habitId, TriggerStatus.COMPLETED, midnightMillis)
        insertTrigger(habitId, TriggerStatus.FIRED, midnightMillis + 1)
        insertTrigger(habitId, TriggerStatus.DISMISSED, midnightMillis + 2)

        val count = triggerDao.countDailyCompletionsSince(habitId, midnightMillis)

        assertEquals("Only COMPLETED counts; FIRED and DISMISSED must not", 1, count)
    }

    @Test
    fun `countDailyCompletionsSince excludes triggers before the cutoff`() = runTest {
        val habitId = insertHabit("hOld")
        // 1ms before cutoff — must not be counted
        insertTrigger(habitId, TriggerStatus.COMPLETED, midnightMillis - 1)

        val count = triggerDao.countDailyCompletionsSince(habitId, midnightMillis)

        assertEquals("Triggers before cutoff must not count", 0, count)
    }

    @Test
    fun `daysWithAnyCompletion counts two completions on the same local day once`() = runTest {
        val habitId = insertHabit("hSameDay")
        // Local 2026-01-15 08:00 and 19:00 — one local day, two different UTC days.
        insertTrigger(habitId, TriggerStatus.COMPLETED, utcMillis("2026-01-15T16:00:00Z"))
        insertTrigger(habitId, TriggerStatus.COMPLETED, utcMillis("2026-01-16T03:00:00Z"))

        val days = triggerDao.daysWithAnyCompletion(offsetMillisUtcMinus8).first()

        assertEquals("Same local day must count once", 1, days)
    }

    @Test
    fun `daysWithAnyCompletion counts completions spanning local midnight as two days`() = runTest {
        val habitId = insertHabit("hMidnight")
        // Local 2026-01-15 23:59 and 2026-01-16 00:01 — same UTC day, two local days.
        insertTrigger(habitId, TriggerStatus.COMPLETED, utcMillis("2026-01-16T07:59:00Z"))
        insertTrigger(habitId, TriggerStatus.COMPLETED, utcMillis("2026-01-16T08:01:00Z"))

        val days = triggerDao.daysWithAnyCompletion(offsetMillisUtcMinus8).first()

        assertEquals("Crossing local midnight must count twice", 2, days)
    }

    @Test
    fun `daysWithAnyCompletion buckets a late local evening completion to that local day`() = runTest {
        val habitId = insertHabit("hEvening")
        // Local 2026-01-15 22:00 and 2026-01-16 10:00 — both land on 2026-01-16 in UTC.
        insertTrigger(habitId, TriggerStatus.COMPLETED, utcMillis("2026-01-16T06:00:00Z"))
        insertTrigger(habitId, TriggerStatus.COMPLETED, utcMillis("2026-01-16T18:00:00Z"))

        val days = triggerDao.daysWithAnyCompletion(offsetMillisUtcMinus8).first()

        assertEquals("Late local evening belongs to the local day, not the next UTC day", 2, days)
    }

    @Test
    fun `growth counters ignore DISMISSED and FIRED triggers`() = runTest {
        val habitId = insertHabit("hNotCompleted")
        insertTrigger(habitId, TriggerStatus.DISMISSED, utcMillis("2026-01-15T16:00:00Z"))
        insertTrigger(habitId, TriggerStatus.FIRED, utcMillis("2026-01-16T16:00:00Z"))

        assertEquals(0, triggerDao.daysWithAnyCompletion(offsetMillisUtcMinus8).first())
        assertEquals(0, triggerDao.daysWithHabitCompletion(habitId, offsetMillisUtcMinus8).first())
        assertEquals(0, triggerDao.totalCompletionsForHabit(habitId).first())
    }

    @Test
    fun `a null habit_id completion counts overall but for no habit`() = runTest {
        val habitId = insertHabit("hOwned")
        insertTrigger(null, TriggerStatus.COMPLETED, utcMillis("2026-01-15T16:00:00Z"))
        insertTrigger(habitId, TriggerStatus.COMPLETED, utcMillis("2026-01-16T16:00:00Z"))

        assertEquals(2, triggerDao.daysWithAnyCompletion(offsetMillisUtcMinus8).first())
        assertEquals(1, triggerDao.daysWithHabitCompletion(habitId, offsetMillisUtcMinus8).first())
        assertEquals(1, triggerDao.totalCompletionsForHabit(habitId).first())
    }

    @Test
    fun `totalCompletionsForHabit counts every completion including several on one day`() = runTest {
        val habitId = insertHabit("hTotal")
        val otherHabitId = insertHabit("hOther")
        // Three completions inside local 2026-01-15, plus one on the next local day.
        insertTrigger(habitId, TriggerStatus.COMPLETED, utcMillis("2026-01-15T16:00:00Z"))
        insertTrigger(habitId, TriggerStatus.COMPLETED, utcMillis("2026-01-15T20:00:00Z"))
        insertTrigger(habitId, TriggerStatus.COMPLETED, utcMillis("2026-01-16T03:00:00Z"))
        insertTrigger(habitId, TriggerStatus.COMPLETED, utcMillis("2026-01-16T16:00:00Z"))
        insertTrigger(otherHabitId, TriggerStatus.COMPLETED, utcMillis("2026-01-16T16:00:00Z"))

        assertEquals(4, triggerDao.totalCompletionsForHabit(habitId).first())
        assertEquals(2, triggerDao.daysWithHabitCompletion(habitId, offsetMillisUtcMinus8).first())
    }

    @Test
    fun `countAnyCompletionsSince counts completions of any habit after the cutoff`() = runTest {
        val habitId = insertHabit("hToday")
        insertTrigger(habitId, TriggerStatus.COMPLETED, midnightMillis - 1)

        assertEquals(0, triggerDao.countAnyCompletionsSince(midnightMillis).first())

        insertTrigger(null, TriggerStatus.COMPLETED, midnightMillis)

        assertEquals(1, triggerDao.countAnyCompletionsSince(midnightMillis).first())
    }
}
