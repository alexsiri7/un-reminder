package net.interstellarai.unreminder.domain

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.db.WindowEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.sentry.Breadcrumb
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HabitAvailabilityServiceTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockHabitRepository: HabitRepository = mockk(relaxed = true)
    private val mockWindowRepository: WindowRepository = mockk(relaxed = true)
    private val mockTriggerRepository: TriggerRepository = mockk(relaxed = true)
    private val mockGeofenceManager: GeofenceManager = mockk(relaxed = true)

    private val currentLocationIdsFlow = MutableStateFlow<Set<Long>>(emptySet())

    private lateinit var service: HabitAvailabilityService

    private val testHabit = HabitEntity(
        id = 1L,
        name = "meditation",
        descriptionLadder = listOf("3 deep breaths", "", "", "20-minute guided meditation", "", ""),
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        currentLocationIdsFlow.value = emptySet()
        every { mockGeofenceManager.currentLocationIds } returns currentLocationIdsFlow.asStateFlow()
        coEvery { mockTriggerRepository.countCompletedSince(any(), any()) } returns 0
        coEvery { mockTriggerRepository.countDailyCompletionsSince(any(), any()) } returns 0
        coEvery { mockTriggerRepository.getLastFiredOrDismissedForHabit(any()) } returns null
        coEvery { mockWindowRepository.getActiveWindows() } returns emptyList()
        service = HabitAvailabilityService(
            mockHabitRepository,
            mockWindowRepository,
            mockTriggerRepository,
            mockGeofenceManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- computeAvailability (internal, with explicit ids) ---

    @Test
    fun `availability is Available when no constraints apply`() = runTest(testDispatcher) {
        val result = service.computeAvailability(testHabit, emptySet(), emptySet())
        assertEquals(AvailabilityStatus.Available, result)
    }

    @Test
    fun `availability includes INACTIVE when habit is inactive`() = runTest(testDispatcher) {
        val inactive = testHabit.copy(active = false)
        val result = service.computeAvailability(inactive, emptySet(), emptySet())
        val status = result as AvailabilityStatus.Unavailable
        assertTrue(UnavailableReason.INACTIVE in status.reasons)
    }

    @Test
    fun `availability includes LOCATION when current geofence does not overlap habit locations`() = runTest(testDispatcher) {
        currentLocationIdsFlow.value = setOf(99L)
        val result = service.computeAvailability(testHabit, setOf(1L, 2L), emptySet())
        val status = result as AvailabilityStatus.Unavailable
        assertTrue(UnavailableReason.LOCATION in status.reasons)
    }

    @Test
    fun `location denials leave a sampled breadcrumb with the habit id and current set`() = runTest(testDispatcher) {
        mockkStatic(Sentry::class)
        try {
            val breadcrumbs = mutableListOf<Breadcrumb>()
            every { Sentry.addBreadcrumb(capture(breadcrumbs)) } just runs
            currentLocationIdsFlow.value = setOf(99L)

            repeat(21) { service.computeAvailability(testHabit, setOf(1L, 2L), emptySet()) }

            assertEquals(2, breadcrumbs.size)
            assertTrue(breadcrumbs.all { it.category == "geofence" })
            assertEquals("1", breadcrumbs.first().getData("habit_id"))
            assertEquals("[99]", breadcrumbs.first().getData("current_ids"))
        } finally {
            unmockkStatic(Sentry::class)
        }
    }

    @Test
    fun `availability omits LOCATION when current geofence overlaps habit locations`() = runTest(testDispatcher) {
        currentLocationIdsFlow.value = setOf(2L, 99L)
        val result = service.computeAvailability(testHabit, setOf(1L, 2L), emptySet())
        assertEquals(AvailabilityStatus.Available, result)
    }

    @Test
    fun `availability includes COMPLETED when at least one COMPLETED trigger today`() = runTest(testDispatcher) {
        coEvery { mockTriggerRepository.countCompletedSince(testHabit.id, any()) } returns 1
        val result = service.computeAvailability(testHabit, emptySet(), emptySet())
        val status = result as AvailabilityStatus.Unavailable
        assertTrue(UnavailableReason.COMPLETED in status.reasons)
    }

    @Test
    fun `availability omits COOLDOWN when cooldownMinutes is 0 even after recent DISMISSED`() = runTest(testDispatcher) {
        val noCooldown = testHabit.copy(cooldownMinutes = 0)
        coEvery { mockTriggerRepository.getLastFiredOrDismissedForHabit(any()) } returns Instant.now().toEpochMilli()
        val result = service.computeAvailability(noCooldown, emptySet(), emptySet())
        assertEquals(AvailabilityStatus.Available, result)
    }

    @Test
    fun `availability includes COOLDOWN when last DISMISSED is within cooldown window`() = runTest(testDispatcher) {
        val cooldownHabit = testHabit.copy(cooldownMinutes = 60)
        val tenMinAgo = Instant.now().toEpochMilli() - 10 * 60 * 1000L
        coEvery { mockTriggerRepository.getLastFiredOrDismissedForHabit(cooldownHabit.id) } returns tenMinAgo
        val result = service.computeAvailability(cooldownHabit, emptySet(), emptySet())
        val status = result as AvailabilityStatus.Unavailable
        assertTrue(UnavailableReason.COOLDOWN in status.reasons)
    }

    @Test
    fun `availability omits COOLDOWN when last DISMISSED is outside cooldown window`() = runTest(testDispatcher) {
        val cooldownHabit = testHabit.copy(cooldownMinutes = 60)
        val twoHoursAgo = Instant.now().toEpochMilli() - 2 * 60 * 60 * 1000L
        coEvery { mockTriggerRepository.getLastFiredOrDismissedForHabit(cooldownHabit.id) } returns twoHoursAgo
        val result = service.computeAvailability(cooldownHabit, emptySet(), emptySet())
        assertEquals(AvailabilityStatus.Available, result)
    }

    @Test
    fun `availability includes DAILY_LIMIT when non-scheduled count meets the limit`() = runTest(testDispatcher) {
        val limit2 = testHabit.copy(dailyLimit = 2, cooldownMinutes = 0)
        coEvery { mockTriggerRepository.countDailyCompletionsSince(limit2.id, any()) } returns 2
        val result = service.computeAvailability(limit2, emptySet(), emptySet())
        val status = result as AvailabilityStatus.Unavailable
        assertTrue(
            "daily limit hit but DAILY_LIMIT not in reasons",
            UnavailableReason.DAILY_LIMIT in status.reasons
        )
    }

    @Test
    fun `availability omits DAILY_LIMIT when non-scheduled count is under the limit`() = runTest(testDispatcher) {
        val limit3 = testHabit.copy(dailyLimit = 3, cooldownMinutes = 0)
        coEvery { mockTriggerRepository.countDailyCompletionsSince(limit3.id, any()) } returns 1
        val result = service.computeAvailability(limit3, emptySet(), emptySet())
        assertEquals(AvailabilityStatus.Available, result)
    }

    // --- computeAvailability (public, fetches ids from repository) ---

    @Test
    fun `computeAvailability fetches ids from habitRepository and delegates`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getLocationIds(testHabit.id) } returns listOf(1L, 2L)
        coEvery { mockHabitRepository.getWindowIds(testHabit.id) } returns emptyList()
        currentLocationIdsFlow.value = setOf(99L)

        val result = service.computeAvailability(testHabit)

        val status = result as AvailabilityStatus.Unavailable
        assertTrue(UnavailableReason.LOCATION in status.reasons)
    }

    @Test
    fun `computeAvailability is Available when habit has no location or window constraints`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getLocationIds(testHabit.id) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(testHabit.id) } returns emptyList()

        val result = service.computeAvailability(testHabit)
        assertEquals(AvailabilityStatus.Available, result)
    }

    // --- computeForAll ---

    @Test
    fun `computeForAll returns a map entry for each habit`() = runTest(testDispatcher) {
        val habit2 = testHabit.copy(id = 2L, name = "exercise")
        coEvery { mockHabitRepository.getLocationIds(any()) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(any()) } returns emptyList()

        val result = service.computeForAll(listOf(testHabit, habit2))

        assertEquals(2, result.size)
        assertTrue(testHabit.id in result)
        assertTrue(habit2.id in result)
    }

    @Test
    fun `computeForAll swallows per-habit exceptions and continues`() = runTest(testDispatcher) {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0

        val habit2 = testHabit.copy(id = 2L, name = "exercise")
        coEvery { mockHabitRepository.getLocationIds(testHabit.id) } throws RuntimeException("db error")
        coEvery { mockHabitRepository.getLocationIds(habit2.id) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(any()) } returns emptyList()

        val result = service.computeForAll(listOf(testHabit, habit2))

        // Both entries are present — error for habit 1 falls back to Available
        assertEquals(2, result.size)
        assertEquals(AvailabilityStatus.Available, result[testHabit.id])
        assertEquals(AvailabilityStatus.Available, result[habit2.id])
        unmockkStatic(android.util.Log::class)
    }

    // --- computeDisplayTiers ---

    private fun givenNoLocationsOrWindows() {
        coEvery { mockHabitRepository.getLocationIds(any()) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(any()) } returns emptyList()
    }

    private fun lastTrigger(habitId: Long, status: TriggerStatus) {
        coEvery { mockTriggerRepository.getLastNForHabit(habitId, 1) } returns listOf(
            TriggerEntity(id = 500L + habitId, habitId = habitId, scheduledAt = Instant.now(), firedAt = Instant.now(), status = status),
        )
    }

    @Test
    fun `a doable habit with no dismissal on record is the top tier`() = runTest(testDispatcher) {
        givenNoLocationsOrWindows()

        assertEquals(mapOf(1L to DisplayTier.DOABLE), service.computeDisplayTiers(listOf(testHabit)))
    }

    @Test
    fun `a doable habit whose last trigger was completed is still the top tier`() = runTest(testDispatcher) {
        givenNoLocationsOrWindows()
        lastTrigger(testHabit.id, TriggerStatus.COMPLETED)

        assertEquals(mapOf(1L to DisplayTier.DOABLE), service.computeDisplayTiers(listOf(testHabit)))
    }

    @Test
    fun `a habit whose most recent trigger was dismissed ranks second even while in cooldown`() = runTest(testDispatcher) {
        val cooldownHabit = testHabit.copy(cooldownMinutes = 60)
        givenNoLocationsOrWindows()
        lastTrigger(cooldownHabit.id, TriggerStatus.DISMISSED)
        coEvery { mockTriggerRepository.getLastFiredOrDismissedForHabit(cooldownHabit.id) } returns Instant.now().toEpochMilli()

        assertEquals(mapOf(1L to DisplayTier.RECENTLY_DISMISSED), service.computeDisplayTiers(listOf(cooldownHabit)))
    }

    @Test
    fun `a paused habit gets no tier even when recently dismissed`() = runTest(testDispatcher) {
        val paused = testHabit.copy(active = false)
        givenNoLocationsOrWindows()
        lastTrigger(paused.id, TriggerStatus.DISMISSED)

        assertEquals(emptyMap<Long, DisplayTier>(), service.computeDisplayTiers(listOf(paused)))
    }

    @Test
    fun `each block lands in its own tier`() = runTest(testDispatcher) {
        val cooling = testHabit.copy(id = 1L, cooldownMinutes = 60)
        val capped = testHabit.copy(id = 2L, dailyLimit = 1)
        val outOfHours = testHabit.copy(id = 3L)
        val elsewhere = testHabit.copy(id = 4L)
        val done = testHabit.copy(id = 5L, dailyLimit = 5)
        coEvery { mockHabitRepository.getLocationIds(any()) } returns emptyList()
        coEvery { mockHabitRepository.getLocationIds(4L) } returns listOf(10L)
        coEvery { mockHabitRepository.getWindowIds(any()) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(3L) } returns listOf(20L)
        coEvery { mockTriggerRepository.getLastFiredOrDismissedForHabit(1L) } returns Instant.now().toEpochMilli()
        coEvery { mockTriggerRepository.countDailyCompletionsSince(2L, any()) } returns 1
        coEvery { mockTriggerRepository.countCompletedSince(5L, any()) } returns 1
        currentLocationIdsFlow.value = setOf(99L)

        val tiers = service.computeDisplayTiers(listOf(cooling, capped, outOfHours, elsewhere, done))

        assertEquals(
            mapOf(
                1L to DisplayTier.PACED,
                2L to DisplayTier.PACED,
                3L to DisplayTier.OUT_OF_HOURS,
                4L to DisplayTier.ELSEWHERE,
                5L to DisplayTier.DONE_TODAY,
            ),
            tiers,
        )
    }

    @Test
    fun `a habit blocked for several reasons takes the least actionable one`() = runTest(testDispatcher) {
        // dailyLimit defaults to 1, so a single completion trips COMPLETED and DAILY_LIMIT together.
        givenNoLocationsOrWindows()
        coEvery { mockTriggerRepository.countCompletedSince(testHabit.id, any()) } returns 1
        coEvery { mockTriggerRepository.countDailyCompletionsSince(testHabit.id, any()) } returns 1

        val status = service.computeAvailability(testHabit) as AvailabilityStatus.Unavailable
        assertEquals(listOf(UnavailableReason.COMPLETED, UnavailableReason.DAILY_LIMIT), status.reasons)
        assertEquals(mapOf(1L to DisplayTier.DONE_TODAY), service.computeDisplayTiers(listOf(testHabit)))
    }

    @Test
    fun `the blocked tiers run from most to least actionable with done today last`() {
        assertEquals(
            listOf(
                DisplayTier.DOABLE,
                DisplayTier.RECENTLY_DISMISSED,
                DisplayTier.PACED,
                DisplayTier.OUT_OF_HOURS,
                DisplayTier.ELSEWHERE,
                DisplayTier.DONE_TODAY,
            ),
            DisplayTier.entries.sorted(),
        )
    }

    @Test
    fun `ranking a blocked habit for display leaves it ineligible to trigger`() = runTest(testDispatcher) {
        givenNoLocationsOrWindows()
        coEvery { mockHabitRepository.getWindowIds(testHabit.id) } returns listOf(20L)

        assertEquals(mapOf(1L to DisplayTier.OUT_OF_HOURS), service.computeDisplayTiers(listOf(testHabit)))
        assertFalse(service.computeAvailability(testHabit).isDoableNow)
    }
}
