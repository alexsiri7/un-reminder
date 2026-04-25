package net.interstellarai.unreminder.service.trigger

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.HabitLevelDescriptionRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.worker.RefillScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Before
import org.junit.Test
import java.time.Instant

class TriggerPipelineTest {

    private lateinit var habitRepository: HabitRepository
    private lateinit var triggerRepository: TriggerRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var variationRepository: VariationRepository
    private lateinit var refillScheduler: RefillScheduler
    private lateinit var levelDescriptionRepository: HabitLevelDescriptionRepository
    private lateinit var pipeline: TriggerPipeline

    private val testHabit = HabitEntity(
        id = 1L,
        name = "meditation",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private val scheduledTrigger = TriggerEntity(
        id = 42L,
        scheduledAt = Instant.now(),
        status = TriggerStatus.SCHEDULED
    )

    @Before
    fun setup() {
        habitRepository = mockk()
        triggerRepository = mockk(relaxUnitFun = true)
        locationRepository = mockk()
        geofenceManager = mockk()
        notificationHelper = mockk(relaxUnitFun = true)
        variationRepository = mockk()
        refillScheduler = mockk(relaxUnitFun = true)
        levelDescriptionRepository = mockk()

        pipeline = TriggerPipeline(
            habitRepository = habitRepository,
            triggerRepository = triggerRepository,
            locationRepository = locationRepository,
            geofenceManager = geofenceManager,
            notificationHelper = notificationHelper,
            variationRepository = variationRepository,
            refillScheduler = refillScheduler,
            levelDescriptionRepository = levelDescriptionRepository,
        )

        every { geofenceManager.currentLocationIds } returns setOf(1L)
        coEvery { locationRepository.getByIds(any()) } returns emptyList()
        coEvery { triggerRepository.getLastFiredForHabit(any()) } returns null
    }

    @Test
    fun `trigger not found - no notification`() = runTest {
        coEvery { triggerRepository.getById(99L) } returns null

        pipeline.execute(99L)

        coVerify(exactly = 0) { notificationHelper.postTriggerNotification(any(), any(), any()) }
    }

    @Test
    fun `trigger already fired - no action`() = runTest {
        val firedTrigger = scheduledTrigger.copy(status = TriggerStatus.FIRED)
        coEvery { triggerRepository.getById(42L) } returns firedTrigger

        pipeline.execute(42L)

        coVerify(exactly = 0) { habitRepository.getEligibleHabits(any(), any()) }
        coVerify(exactly = 0) { notificationHelper.postTriggerNotification(any(), any(), any()) }
    }

    @Test
    fun `no eligible habits - dismisses trigger and no notification`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns emptyList()

        pipeline.execute(42L)

        coVerify { triggerRepository.updateOutcome(42L, TriggerStatus.DISMISSED) }
        coVerify(exactly = 0) { notificationHelper.postTriggerNotification(any(), any(), any()) }
    }

    @Test
    fun `pool has variants - uses variation text`() = runTest {
        val variation = VariationEntity(
            id = 7L, habitId = 1L, text = "Cloud notification body",
            promptFingerprint = "fp", generatedAt = Instant.now(), consumedAt = null
        )
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { variationRepository.pickRandomUnused(1L) } returns variation
        coEvery { variationRepository.needsRefill(1L) } returns false

        pipeline.execute(42L)

        coVerify { triggerRepository.updateFired(42L, 1L, "Cloud notification body") }
        coVerify {
            notificationHelper.postTriggerNotification(
                triggerId = 42L,
                promptText = "Cloud notification body",
                habitName = "meditation"
            )
        }
        coVerify(exactly = 0) { refillScheduler.enqueueForHabit(any()) }
    }

    @Test
    fun `pool empty - falls back to habit name when level description is blank`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { variationRepository.pickRandomUnused(1L) } returns null
        coEvery { levelDescriptionRepository.getDescriptionForLevel(1L, 2) } returns ""

        pipeline.execute(42L)

        coVerify { triggerRepository.updateFired(42L, 1L, "meditation") }
        coVerify {
            notificationHelper.postTriggerNotification(
                triggerId = 42L,
                promptText = "meditation",
                habitName = "meditation"
            )
        }
        coVerify { refillScheduler.enqueueForHabit(1L) }
    }

    @Test
    fun `pool empty - uses level description when available`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { variationRepository.pickRandomUnused(1L) } returns null
        coEvery { levelDescriptionRepository.getDescriptionForLevel(1L, 2) } returns
            "Take three deep breaths"

        pipeline.execute(42L)

        coVerify { triggerRepository.updateFired(42L, 1L, "Take three deep breaths") }
        coVerify {
            notificationHelper.postTriggerNotification(
                triggerId = 42L,
                promptText = "Take three deep breaths",
                habitName = "meditation"
            )
        }
    }

    @Test
    fun `pool has variants and needsRefill true - enqueues refill`() = runTest {
        val variation = VariationEntity(
            id = 7L, habitId = 1L, text = "Cloud notification body",
            promptFingerprint = "fp", generatedAt = Instant.now(), consumedAt = null
        )
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { variationRepository.pickRandomUnused(1L) } returns variation
        coEvery { variationRepository.needsRefill(1L) } returns true

        pipeline.execute(42L)

        coVerify { triggerRepository.updateFired(42L, 1L, "Cloud notification body") }
        coVerify(exactly = 1) { refillScheduler.enqueueForHabit(1L) }
    }

    @Test
    fun `pickRandomUnused throws - falls back to habit name and enqueues refill`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { variationRepository.pickRandomUnused(1L) } throws RuntimeException("db error")
        coEvery { levelDescriptionRepository.getDescriptionForLevel(1L, 2) } returns null

        pipeline.execute(42L)

        coVerify { triggerRepository.updateFired(42L, 1L, "meditation") }
        coVerify { refillScheduler.enqueueForHabit(1L) }
    }

    @Test
    fun `pickRandomUnused throws CancellationException - propagates`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { variationRepository.pickRandomUnused(1L) } throws CancellationException("cancelled")

        try {
            pipeline.execute(42L)
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            // expected
        }

        coVerify(exactly = 0) { triggerRepository.updateFired(any(), any(), any()) }
    }

    @Test
    fun `location name passed to variation resolution`() = runTest {
        val loc = LocationEntity(id = 1L, name = "Home", lat = 0.0, lng = 0.0, radiusM = 100f)
        val variation = VariationEntity(
            id = 7L, habitId = 1L, text = "Time for meditation!",
            promptFingerprint = "fp", generatedAt = Instant.now(), consumedAt = null
        )
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { locationRepository.getByIds(setOf(1L)) } returns listOf(loc)
        coEvery { variationRepository.pickRandomUnused(1L) } returns variation
        coEvery { variationRepository.needsRefill(1L) } returns false

        pipeline.execute(42L)

        coVerify { triggerRepository.updateFired(42L, 1L, "Time for meditation!") }
    }

    @Test
    fun `stale location id - uses any location fallback`() = runTest {
        val variation = VariationEntity(
            id = 7L, habitId = 1L, text = "Time!",
            promptFingerprint = "fp", generatedAt = Instant.now(), consumedAt = null
        )
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { locationRepository.getByIds(any()) } returns emptyList()
        coEvery { variationRepository.pickRandomUnused(1L) } returns variation
        coEvery { variationRepository.needsRefill(1L) } returns false

        pipeline.execute(42L)

        coVerify { triggerRepository.updateFired(42L, 1L, "Time!") }
    }

    @Test
    fun `pipeline exception is caught and does not propagate`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { variationRepository.pickRandomUnused(1L) } throws CancellationException("test")

        try {
            pipeline.execute(42L)
            fail("Expected CancellationException")
        } catch (_: CancellationException) { }
    }

    @Test
    fun `pipeline runtime exception dismisses the trigger`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } throws RuntimeException("boom")

        pipeline.execute(42L)

        coVerify { triggerRepository.updateOutcome(42L, TriggerStatus.DISMISSED) }
    }
}

class TriggerPipelineWeightTest {

    @Test
    fun `habit never fired gets max weight`() {
        val w = TriggerPipeline.computeWeight(null, nowMillis = 0L)
        assertEquals(TriggerPipeline.MAX_WEIGHT, w, 0.001)
    }

    @Test
    fun `weight formula at 120 minutes gives 2_0`() {
        val now = 120 * 60_000L
        val w = TriggerPipeline.computeWeight(lastFiredMillis = 0L, nowMillis = now)
        assertEquals(2.0, w, 0.001)
    }

    @Test
    fun `more recent completion gives lower weight`() {
        val now = 1_000_000L
        val recent = TriggerPipeline.computeWeight(now - 100 * 60_000L, now)
        val older = TriggerPipeline.computeWeight(now - 500 * 60_000L, now)
        assertTrue(recent < older)
    }

    @Test
    fun `weight is capped at MAX_WEIGHT for very old completions`() {
        val now = 100_000 * 60_000L
        val w = TriggerPipeline.computeWeight(lastFiredMillis = 0L, nowMillis = now)
        assertEquals(TriggerPipeline.MAX_WEIGHT, w, 0.001)
    }

    @Test
    fun `weight is at least 1_0 for recently eligible habit`() {
        val now = 90 * 60_000L
        val w = TriggerPipeline.computeWeight(lastFiredMillis = 0L, nowMillis = now)
        assertTrue(w >= 1.0)
    }

    @Test
    fun `pickWeighted with single habit always returns that habit`() {
        val habit = HabitEntity(
            id = 1L, name = "test",
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
        )
        val result = TriggerPipeline.pickWeighted(listOf(habit), emptyMap(), nowMillis = 0L)
        assertEquals(habit, result)
    }

    @Test
    fun `pickWeighted with null lastFired biases toward never-done habit`() {
        val neverDone = HabitEntity(
            id = 1L, name = "never",
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
        )
        val recentlyDone = HabitEntity(
            id = 2L, name = "recent",
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
        )
        val lastFiredMap = mapOf(1L to null, 2L to 0L)
        val now = 91 * 60_000L
        // Expected neverDone rate ≈ 88% (MAX_WEIGHT / (MAX_WEIGHT + ~1.76)); threshold 600 gives 28pt margin
        val neverDoneCount = (1..1000).count {
            TriggerPipeline.pickWeighted(listOf(neverDone, recentlyDone), lastFiredMap, now) == neverDone
        }
        assertTrue("Expected >600/1000 picks for never-done, got $neverDoneCount", neverDoneCount > 600)
    }

    @Test
    fun `weight floors at 1_0 when lastFired is in the future (clock skew)`() {
        val now = 60 * 60_000L
        val futureLastFired = now + 30 * 60_000L // 30 minutes in the future
        val w = TriggerPipeline.computeWeight(futureLastFired, now)
        assertTrue("Expected weight >= 1.0 for future lastFired, got $w", w >= 1.0)
    }
}
