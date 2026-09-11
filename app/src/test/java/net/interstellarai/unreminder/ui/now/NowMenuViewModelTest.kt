package net.interstellarai.unreminder.ui.now

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.HabitLevelDescriptionRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.AvailabilityStatus
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.domain.UnavailableReason
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowMenuViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val habitRepository: HabitRepository = mockk()
    private val availabilityService: HabitAvailabilityService = mockk()
    private val levelDescriptionRepository: HabitLevelDescriptionRepository = mockk()
    private val triggerRepository: TriggerRepository = mockk()
    private val dismissalTracker: DismissalTracker = mockk(relaxUnitFun = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { triggerRepository.daysWithAnyCompletion() } returns flowOf(0)
        coEvery { levelDescriptionRepository.getDescriptionForLevel(any(), any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun habit(id: Long, level: Int = 2) = HabitEntity(id = id, name = "habit $id", dedicationLevel = level)

    private fun givenHabits(
        habits: List<HabitEntity>,
        availability: Map<Long, AvailabilityStatus>,
    ) {
        every { habitRepository.getAll() } returns flowOf(habits)
        coEvery { availabilityService.computeForAll(habits) } returns availability
    }

    private fun allAvailable(habits: List<HabitEntity>) = habits.associate { it.id to AvailabilityStatus.Available }

    private fun buildViewModel() = NowMenuViewModel(
        habitRepository,
        availabilityService,
        levelDescriptionRepository,
        triggerRepository,
        dismissalTracker,
    )

    private fun NowMenuViewModel.menu() = uiState.value as NowMenuUiState.Menu

    @Test
    fun `exactly 3 shown when more than 3 are eligible`() = runTest(testDispatcher) {
        val habits = (1L..5L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(3, vm.menu().items.size)
        assertTrue(vm.menu().canLoadMore)
    }

    @Test
    fun `all shown when fewer than 3 are eligible`() = runTest(testDispatcher) {
        val habits = (1L..2L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(setOf(1L, 2L), vm.menu().items.map { it.habitId }.toSet())
        assertFalse(vm.menu().canLoadMore)
    }

    @Test
    fun `load more appends the next 3 without repeating an already shown habit`() = runTest(testDispatcher) {
        val habits = (1L..7L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()
        val firstPage = vm.menu().items

        vm.loadMore()

        val secondPage = vm.menu().items
        assertEquals(6, secondPage.size)
        assertEquals(firstPage, secondPage.take(3))
        assertEquals(6, secondPage.map { it.habitId }.distinct().size)
        assertTrue(vm.menu().canLoadMore)
    }

    @Test
    fun `load more is unavailable once the eligible set is exhausted`() = runTest(testDispatcher) {
        val habits = (1L..4L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.loadMore()

        assertEquals(4, vm.menu().items.size)
        assertFalse(vm.menu().canLoadMore)
    }

    @Test
    fun `ineligible habits never appear`() = runTest(testDispatcher) {
        val habits = (1L..6L).map { habit(it) }
        givenHabits(
            habits,
            mapOf(
                1L to AvailabilityStatus.Available,
                2L to AvailabilityStatus.Unavailable(listOf(UnavailableReason.COMPLETED)),
                3L to AvailabilityStatus.NewHabit,
                4L to AvailabilityStatus.Unavailable(listOf(UnavailableReason.TIME_WINDOW)),
                5L to AvailabilityStatus.Available,
                6L to AvailabilityStatus.Unavailable(listOf(UnavailableReason.INACTIVE, UnavailableReason.LOCATION)),
            ),
        )
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()
        vm.loadMore()

        assertEquals(setOf(1L, 3L, 5L), vm.menu().items.map { it.habitId }.toSet())
        assertFalse(vm.menu().canLoadMore)
    }

    @Test
    fun `rows carry the description for the habit's current level`() = runTest(testDispatcher) {
        val habits = listOf(habit(1L, level = 4))
        givenHabits(habits, allAvailable(habits))
        coEvery { levelDescriptionRepository.getDescriptionForLevel(1L, 4) } returns "ten minutes"
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals("ten minutes", vm.menu().items.single().description)
    }

    @Test
    fun `completing inserts a COMPLETED menu trigger and runs promotion`() = runTest(testDispatcher) {
        val habits = (1L..4L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        val inserted = slot<TriggerEntity>()
        coEvery { triggerRepository.insert(capture(inserted)) } returns 99L
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()
        val completedId = vm.menu().items.first().habitId

        vm.complete(completedId)
        advanceUntilIdle()

        assertEquals(completedId, inserted.captured.habitId)
        assertEquals(TriggerStatus.COMPLETED, inserted.captured.status)
        assertEquals("menu", inserted.captured.source)
        assertNotNull(inserted.captured.firedAt)
        assertEquals(inserted.captured.scheduledAt, inserted.captured.firedAt)
        coVerify(exactly = 1) { dismissalTracker.onCompleted(99L) }
    }

    @Test
    fun `completing removes the habit and backfills from the held shuffle`() = runTest(testDispatcher) {
        val habits = (1L..4L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        coEvery { triggerRepository.insert(any()) } returns 99L
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()
        val before = vm.menu().items.map { it.habitId }
        val completedId = before.first()

        vm.complete(completedId)

        val after = vm.menu().items.map { it.habitId }
        assertEquals(3, after.size)
        assertFalse(completedId in after)
        assertEquals(before.drop(1), after.take(2))
        assertFalse(vm.menu().canLoadMore)
    }

    @Test
    fun `completing the last eligible habit falls through to the empty state`() = runTest(testDispatcher) {
        val habits = listOf(habit(1L))
        every { habitRepository.getAll() } returns flowOf(habits)
        coEvery { availabilityService.computeForAll(habits) } returnsMany listOf(
            allAvailable(habits),
            mapOf(1L to AvailabilityStatus.Unavailable(listOf(UnavailableReason.COMPLETED))),
        )
        coEvery { triggerRepository.insert(any()) } returns 99L
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.complete(1L)
        advanceUntilIdle()

        assertEquals(NowMenuUiState.NothingDoable(UnavailableReason.COMPLETED), vm.uiState.value)
    }

    @Test
    fun `empty state reports the dominant unavailability reason`() = runTest(testDispatcher) {
        val habits = (1L..3L).map { habit(it) }
        givenHabits(
            habits,
            mapOf(
                1L to AvailabilityStatus.Unavailable(listOf(UnavailableReason.TIME_WINDOW)),
                2L to AvailabilityStatus.Unavailable(listOf(UnavailableReason.TIME_WINDOW, UnavailableReason.COOLDOWN)),
                3L to AvailabilityStatus.Unavailable(listOf(UnavailableReason.LOCATION)),
            ),
        )
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(NowMenuUiState.NothingDoable(UnavailableReason.TIME_WINDOW), vm.uiState.value)
    }

    @Test
    fun `empty state ignores paused habits unless every habit is paused`() = runTest(testDispatcher) {
        val habits = (1L..3L).map { habit(it) }
        givenHabits(
            habits,
            mapOf(
                1L to AvailabilityStatus.Unavailable(listOf(UnavailableReason.INACTIVE, UnavailableReason.LOCATION)),
                2L to AvailabilityStatus.Unavailable(listOf(UnavailableReason.INACTIVE, UnavailableReason.LOCATION)),
                3L to AvailabilityStatus.Unavailable(listOf(UnavailableReason.COMPLETED)),
            ),
        )
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(NowMenuUiState.NothingDoable(UnavailableReason.COMPLETED), vm.uiState.value)

        givenHabits(
            habits,
            habits.associate { it.id to AvailabilityStatus.Unavailable(listOf(UnavailableReason.INACTIVE)) },
        )
        vm.refresh()
        advanceUntilIdle()
        assertEquals(NowMenuUiState.NothingDoable(UnavailableReason.INACTIVE), vm.uiState.value)
    }

    @Test
    fun `no habits at all yields the add-a-habit state`() = runTest(testDispatcher) {
        givenHabits(emptyList(), emptyMap())
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(NowMenuUiState.NoHabits, vm.uiState.value)
    }
}
