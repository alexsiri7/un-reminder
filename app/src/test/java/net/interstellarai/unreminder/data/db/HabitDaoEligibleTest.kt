package net.interstellarai.unreminder.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.domain.model.TriggerStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class HabitDaoEligibleTest {

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

    /**
     * Calls the DAO with cutoffs aligned to midnight so that ONLY the new
     * daily-limit clause can exclude a habit. The completed/dismissed cooldowns
     * use strict-greater-than against the same epoch, so a trigger fired at
     * exactly midnight is not excluded by them — that isolates the new clause.
     */
    private suspend fun queryEligible(): List<HabitEntity> = habitDao.getEligibleHabits(
        locationIds = listOf(-1L),
        completedCutoff = midnightMillis,
        nowEpochMillis = midnightMillis,
        startOfDayCutoff = midnightMillis,
        currentSecondOfDay = 0,
        dayOfWeekBit = 1
    )

    private suspend fun queryEligibleAt(nowMillis: Long): List<HabitEntity> = habitDao.getEligibleHabits(
        locationIds = listOf(-1L),
        completedCutoff = midnightMillis,
        nowEpochMillis = nowMillis,
        startOfDayCutoff = midnightMillis,
        currentSecondOfDay = 0,
        dayOfWeekBit = 1
    )

    private suspend fun insertHabit(name: String, dailyLimit: Int = 1, cooldownMinutes: Int = 180): Long =
        habitDao.insert(HabitEntity(name = name, dailyLimit = dailyLimit, cooldownMinutes = cooldownMinutes))

    private suspend fun insertTrigger(habitId: Long, status: TriggerStatus, firedAt: Instant?) {
        triggerDao.insert(
            TriggerEntity(
                habitId = habitId,
                scheduledAt = Instant.ofEpochMilli(midnightMillis),
                firedAt = firedAt,
                status = status
            )
        )
    }

    @Test
    fun `dailyLimit 1 with no triggers today is eligible`() = runTest {
        insertHabit("h1", dailyLimit = 1)

        val eligible = queryEligible()

        assertEquals(1, eligible.size)
        assertEquals("h1", eligible[0].name)
    }

    @Test
    fun `dailyLimit 1 with one COMPLETED trigger today is excluded`() = runTest {
        val id = insertHabit("h2", dailyLimit = 1)
        insertTrigger(id, TriggerStatus.COMPLETED, Instant.ofEpochMilli(midnightMillis))

        val eligible = queryEligible()

        assertTrue(eligible.none { it.id == id })
    }

    @Test
    fun `dailyLimit 2 with one DISMISSED trigger today is eligible`() = runTest {
        // cooldownMinutes = 0 isolates the daily-limit clause from the new per-row cooldown branch.
        val id = insertHabit("h3", dailyLimit = 2, cooldownMinutes = 0)
        insertTrigger(id, TriggerStatus.DISMISSED, Instant.ofEpochMilli(midnightMillis))

        val eligible = queryEligible()

        assertTrue(eligible.any { it.id == id })
    }

    @Test
    fun `dailyLimit 2 with two FIRED triggers today is excluded`() = runTest {
        val id = insertHabit("h4", dailyLimit = 2)
        insertTrigger(id, TriggerStatus.FIRED, Instant.ofEpochMilli(midnightMillis))
        insertTrigger(id, TriggerStatus.FIRED, Instant.ofEpochMilli(midnightMillis + 1))

        val eligible = queryEligible()

        assertTrue(eligible.none { it.id == id })
    }

    @Test
    fun `dailyLimit 1 with one COMPLETED trigger from yesterday is eligible`() = runTest {
        val id = insertHabit("h5", dailyLimit = 1)
        // 1ms before today's local midnight = yesterday
        insertTrigger(id, TriggerStatus.COMPLETED, Instant.ofEpochMilli(midnightMillis - 1))

        val eligible = queryEligible()

        assertTrue(eligible.any { it.id == id })
    }

    @Test
    fun `dailyLimit 1 with one SCHEDULED trigger today is eligible`() = runTest {
        val id = insertHabit("h6", dailyLimit = 1)
        // SCHEDULED triggers have firedAt = null and must not count toward the cap
        insertTrigger(id, TriggerStatus.SCHEDULED, firedAt = null)

        val eligible = queryEligible()

        assertTrue(eligible.any { it.id == id })
    }

    @Test
    fun `cooldown 180 with DISMISSED 2h ago is excluded`() = runTest {
        val twoHoursAgo = Instant.now().minus(Duration.ofHours(2)).toEpochMilli()
        val now = Instant.now().toEpochMilli()
        val id = insertHabit("hCooldown180Excluded", cooldownMinutes = 180, dailyLimit = 999)
        insertTrigger(id, TriggerStatus.DISMISSED, Instant.ofEpochMilli(twoHoursAgo))

        val eligible = queryEligibleAt(now)

        assertTrue(eligible.none { it.id == id })
    }

    @Test
    fun `cooldown 180 with DISMISSED 4h ago is eligible`() = runTest {
        val fourHoursAgo = Instant.now().minus(Duration.ofHours(4)).toEpochMilli()
        val now = Instant.now().toEpochMilli()
        val id = insertHabit("hCooldown180Eligible", cooldownMinutes = 180, dailyLimit = 999)
        insertTrigger(id, TriggerStatus.DISMISSED, Instant.ofEpochMilli(fourHoursAgo))

        val eligible = queryEligibleAt(now)

        assertTrue(eligible.any { it.id == id })
    }

    @Test
    fun `cooldown 0 with DISMISSED 1 minute ago is eligible`() = runTest {
        val oneMinAgo = Instant.now().minusSeconds(60).toEpochMilli()
        val now = Instant.now().toEpochMilli()
        val id = insertHabit("hCooldown0", cooldownMinutes = 0, dailyLimit = 999)
        insertTrigger(id, TriggerStatus.DISMISSED, Instant.ofEpochMilli(oneMinAgo))

        val eligible = queryEligibleAt(now)

        assertTrue(eligible.any { it.id == id })
    }

    @Test
    fun `cooldown 60 with FIRED 30 minutes ago is excluded`() = runTest {
        val thirtyMinAgo = Instant.now().minusSeconds(30 * 60).toEpochMilli()
        val now = Instant.now().toEpochMilli()
        val id = insertHabit("hCooldown60", cooldownMinutes = 60, dailyLimit = 999)
        insertTrigger(id, TriggerStatus.FIRED, Instant.ofEpochMilli(thirtyMinAgo))

        val eligible = queryEligibleAt(now)

        assertTrue(eligible.none { it.id == id })
    }

    @Test
    fun `cooldown 0 with FIRED 1 minute ago is eligible`() = runTest {
        val oneMinAgo = Instant.now().minusSeconds(60).toEpochMilli()
        val now = Instant.now().toEpochMilli()
        val id = insertHabit("hCooldown0Fired", cooldownMinutes = 0, dailyLimit = 999)
        insertTrigger(id, TriggerStatus.FIRED, Instant.ofEpochMilli(oneMinAgo))

        val eligible = queryEligibleAt(now)

        assertTrue(eligible.any { it.id == id })
    }

    @Test
    fun `cooldown 180 with DISMISSED exactly at boundary is eligible`() = runTest {
        // SQL uses strict `fired_at > (now - cooldown_minutes * 60 * 1000)`,
        // so a trigger fired exactly at the boundary is eligible (not excluded).
        val now = Instant.now().toEpochMilli()
        val exactlyAtBoundary = now - 180L * 60L * 1000L
        val id = insertHabit("hCooldownBoundary", cooldownMinutes = 180, dailyLimit = 999)
        insertTrigger(id, TriggerStatus.DISMISSED, Instant.ofEpochMilli(exactlyAtBoundary))

        val eligible = queryEligibleAt(now)

        assertTrue(eligible.any { it.id == id })
    }
}
