package com.alexsiri7.unreminder.service.trigger

import com.alexsiri7.unreminder.data.db.HabitEntity
import com.alexsiri7.unreminder.data.db.LocationEntity
import com.alexsiri7.unreminder.data.db.TriggerEntity
import com.alexsiri7.unreminder.data.repository.HabitRepository
import com.alexsiri7.unreminder.data.repository.LocationRepository
import com.alexsiri7.unreminder.data.repository.TriggerRepository
import com.alexsiri7.unreminder.domain.model.TriggerStatus
import com.alexsiri7.unreminder.service.geofence.GeofenceManager
import com.alexsiri7.unreminder.service.llm.PromptGenerator
import com.alexsiri7.unreminder.service.notification.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class TriggerPipelineTest {

    private lateinit var habitRepository: HabitRepository
    private lateinit var triggerRepository: TriggerRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var promptGenerator: PromptGenerator
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var pipeline: TriggerPipeline

    private val testHabit = HabitEntity(
        id = 1L,
        name = "meditation",
        fullDescription = "20-minute guided meditation",
        lowFloorDescription = "3 deep breaths",
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
        promptGenerator = mockk()
        notificationHelper = mockk(relaxUnitFun = true)

        pipeline = TriggerPipeline(
            habitRepository = habitRepository,
            triggerRepository = triggerRepository,
            locationRepository = locationRepository,
            geofenceManager = geofenceManager,
            promptGenerator = promptGenerator,
            notificationHelper = notificationHelper
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
    fun `happy path - notification posted`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { promptGenerator.generate(any(), any(), any()) } returns "Time for meditation!"

        pipeline.execute(42L)

        coVerify { triggerRepository.updateFired(42L, 1L, "Time for meditation!") }
        coVerify {
            notificationHelper.postTriggerNotification(
                triggerId = 42L,
                promptText = "Time for meditation!",
                habitName = "meditation"
            )
        }
    }

    @Test
    fun `pipeline exception is caught and does not propagate`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { promptGenerator.generate(any(), any(), any()) } throws RuntimeException("LLM error")

        pipeline.execute(42L)

        coVerify(exactly = 0) { notificationHelper.postTriggerNotification(any(), any(), any()) }
    }

    @Test
    fun `pipeline exception dismisses the trigger`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { promptGenerator.generate(any(), any(), any()) } throws RuntimeException("LLM error")

        pipeline.execute(42L)

        coVerify { triggerRepository.updateOutcome(42L, TriggerStatus.DISMISSED) }
    }

    @Test
    fun `happy path - location name passed to prompt generator`() = runTest {
        val loc = LocationEntity(id = 1L, name = "Home", lat = 0.0, lng = 0.0, radiusM = 100f)
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { locationRepository.getByIds(setOf(1L)) } returns listOf(loc)
        coEvery { promptGenerator.generate(any(), "Home", any()) } returns "Time for meditation!"

        pipeline.execute(42L)

        coVerify { promptGenerator.generate(testHabit, "Home", any()) }
    }

    @Test
    fun `stale location id - prompt receives any location fallback`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns scheduledTrigger
        coEvery { habitRepository.getEligibleHabits(any(), any()) } returns listOf(testHabit)
        coEvery { locationRepository.getByIds(any()) } returns emptyList()
        coEvery { promptGenerator.generate(any(), "any location", any()) } returns "Time!"

        pipeline.execute(42L)

        coVerify { promptGenerator.generate(testHabit, "any location", any()) }
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
            fullDescription = "full", lowFloorDescription = "low",
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
        )
        val result = TriggerPipeline.pickWeighted(listOf(habit), emptyMap(), nowMillis = 0L)
        assertEquals(habit, result)
    }

    @Test
    fun `pickWeighted with null lastFired biases toward never-done habit`() {
        val neverDone = HabitEntity(
            id = 1L, name = "never",
            fullDescription = "f", lowFloorDescription = "l",
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
        )
        val recentlyDone = HabitEntity(
            id = 2L, name = "recent",
            fullDescription = "f", lowFloorDescription = "l",
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
        )
        val lastFiredMap = mapOf(1L to null, 2L to 0L)
        val now = 91 * 60_000L
        val neverDoneCount = (1..1000).count {
            TriggerPipeline.pickWeighted(listOf(neverDone, recentlyDone), lastFiredMap, now) == neverDone
        }
        assertTrue("Expected >600/1000 picks for never-done, got $neverDoneCount", neverDoneCount > 600)
    }
}
