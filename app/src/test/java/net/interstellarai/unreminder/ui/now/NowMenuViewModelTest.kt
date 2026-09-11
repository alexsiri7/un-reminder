package net.interstellarai.unreminder.ui.now

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.repository.HabitLevelDescriptionRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.domain.AvailabilityStatus
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.domain.UnavailableReason
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.notification.MascotSprites
import net.interstellarai.unreminder.service.notification.SpriteResolver
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import net.interstellarai.unreminder.widget.WidgetRefresher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NowMenuViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val habitRepository: HabitRepository = mockk()
    private val availabilityService: HabitAvailabilityService = mockk()
    private val levelDescriptionRepository: HabitLevelDescriptionRepository = mockk()
    private val triggerRepository: TriggerRepository = mockk()
    private val variationRepository: VariationRepository = mockk(relaxUnitFun = true)
    private val spriteResolver = SpriteResolver()
    private val dismissalTracker: DismissalTracker = mockk(relaxUnitFun = true)
    private val widgetRefresher: WidgetRefresher = mockk(relaxUnitFun = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { triggerRepository.daysWithAnyCompletion() } returns flowOf(0)
        coEvery { levelDescriptionRepository.getDescriptionForLevel(any(), any()) } returns null
        coEvery { variationRepository.peekUnusedVariation(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
        Dispatchers.resetMain()
    }

    private fun habit(id: Long, level: Int = 2) = HabitEntity(id = id, name = "habit $id", dedicationLevel = level)

    private fun variation(id: Long, habitId: Long, text: String = "variant $id", spriteTag: String? = null) =
        VariationEntity(id = id, habitId = habitId, text = text, promptFingerprint = "fp", generatedAt = Instant.EPOCH, spriteTag = spriteTag)

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
        variationRepository,
        spriteResolver,
        dismissalTracker,
        widgetRefresher,
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
    fun `rows carry a peeked variant and the sprite its tag names`() = runTest(testDispatcher) {
        val habits = listOf(habit(1L, level = 4))
        givenHabits(habits, allAvailable(habits))
        coEvery { levelDescriptionRepository.getDescriptionForLevel(1L, 4) } returns "ten minutes"
        val sprite = MascotSprites.entries[3]
        coEvery { variationRepository.peekUnusedVariation(1L) } returns variation(7L, 1L, "sit like a wizard", sprite.tag)
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        val row = vm.menu().items.single()
        assertEquals("sit like a wizard", row.text)
        assertEquals(7L, row.variationId)
        assertEquals(sprite.drawableRes, row.spriteRes)
    }

    @Test
    fun `with an empty pool every doable habit falls back to its level description and a rotation sprite`() = runTest(testDispatcher) {
        val habits = (1L..3L).map { habit(it, level = 4) }
        givenHabits(habits, allAvailable(habits))
        habits.forEach { coEvery { levelDescriptionRepository.getDescriptionForLevel(it.id, 4) } returns "ten minutes for ${it.id}" }
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        val rows = vm.menu().items
        assertEquals(setOf(1L, 2L, 3L), rows.map { it.habitId }.toSet())
        for (row in rows) {
            assertEquals("ten minutes for ${row.habitId}", row.text)
            assertEquals(null, row.variationId)
            assertEquals(spriteResolver.resolve(null, rotationSeed = row.habitId), row.spriteRes)
        }
    }

    @Test
    fun `a blank level description leaves the row with only its name and sprite`() = runTest(testDispatcher) {
        val habits = listOf(habit(1L))
        givenHabits(habits, allAvailable(habits))
        coEvery { levelDescriptionRepository.getDescriptionForLevel(1L, 2) } returns " "
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(null, vm.menu().items.single().text)
        assertEquals(spriteResolver.resolve(null, rotationSeed = 1L), vm.menu().items.single().spriteRes)
    }

    @Test
    fun `rendering the menu only peeks and never consumes or refills`() = runTest(testDispatcher) {
        val habits = (1L..2L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        coEvery { variationRepository.peekUnusedVariation(1L) } returns variation(7L, 1L)
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        coVerify(exactly = 2) { variationRepository.peekUnusedVariation(1L) }
        coVerify(exactly = 2) { variationRepository.peekUnusedVariation(2L) }
        confirmVerified(variationRepository)
    }

    @Test
    fun `a row's variant and sprite are held across load more`() = runTest(testDispatcher) {
        val habits = (1L..5L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        habits.forEach { h ->
            coEvery { variationRepository.peekUnusedVariation(h.id) } returnsMany listOf(
                variation(h.id * 10, h.id, spriteTag = MascotSprites.entries[0].tag),
                variation(h.id * 10 + 1, h.id, spriteTag = MascotSprites.entries[1].tag),
            )
        }
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()
        val firstPage = vm.menu().items

        vm.loadMore()
        advanceUntilIdle()

        val secondPage = vm.menu().items
        assertEquals(firstPage, secondPage.take(3))
        for (row in secondPage) {
            assertEquals(row.habitId * 10, row.variationId)
            assertEquals(MascotSprites.entries[0].drawableRes, row.spriteRes)
        }
        habits.forEach { coVerify(exactly = 1) { variationRepository.peekUnusedVariation(it.id) } }
    }

    @Test
    fun `completing marks exactly the displayed variant consumed`() = runTest(testDispatcher) {
        val habits = (1L..3L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        habits.forEach { h -> coEvery { variationRepository.peekUnusedVariation(h.id) } returns variation(h.id * 10, h.id) }
        coEvery { triggerRepository.insert(any()) } returns 99L
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()
        val completed = vm.menu().items.first()

        vm.complete(completed.habitId)
        advanceUntilIdle()

        coVerify(exactly = 1) { variationRepository.markConsumed(completed.habitId * 10) }
        coVerify(exactly = 1) { variationRepository.markConsumed(any()) }
    }

    @Test
    fun `a failed consume after the write keeps the habit completed and promoted`() = runTest(testDispatcher) {
        val habits = (1L..3L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        habits.forEach { h -> coEvery { variationRepository.peekUnusedVariation(h.id) } returns variation(h.id * 10, h.id) }
        coEvery { triggerRepository.insert(any()) } returns 99L
        coEvery { variationRepository.markConsumed(any()) } throws IllegalStateException("variations table locked")
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()
        val completedId = vm.menu().items.first().habitId

        vm.complete(completedId)
        advanceUntilIdle()

        assertFalse(completedId in vm.menu().items.map { it.habitId })
        coVerifyOrder {
            triggerRepository.insert(any())
            dismissalTracker.onCompleted(99L)
            variationRepository.markConsumed(completedId * 10)
        }
        verify(exactly = 1) { widgetRefresher.refresh() }
    }

    @Test
    fun `completing a fallback row consumes nothing`() = runTest(testDispatcher) {
        val habits = (1L..3L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        coEvery { triggerRepository.insert(any()) } returns 99L
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.complete(vm.menu().items.first().habitId)
        advanceUntilIdle()

        coVerify(exactly = 0) { variationRepository.markConsumed(any()) }
    }

    @Test
    fun `a failed completion write consumes nothing`() = runTest(testDispatcher) {
        val habits = (1L..3L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        habits.forEach { h -> coEvery { variationRepository.peekUnusedVariation(h.id) } returns variation(h.id * 10, h.id) }
        coEvery { triggerRepository.insert(any()) } throws IllegalStateException("disk full")
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.complete(vm.menu().items.first().habitId)
        advanceUntilIdle()

        coVerify(exactly = 0) { variationRepository.markConsumed(any()) }
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
        verify(exactly = 1) { widgetRefresher.refresh() }
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
    fun `a failed completion write puts the habit back where it was`() = runTest(testDispatcher) {
        val habits = (1L..4L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        coEvery { triggerRepository.insert(any()) } throws IllegalStateException("disk full")
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()
        val before = vm.menu().items.map { it.habitId }
        val failedId = before[1]

        vm.complete(failedId)
        assertFalse(failedId in vm.menu().items.map { it.habitId })
        advanceUntilIdle()

        assertEquals(before, vm.menu().items.map { it.habitId })
        assertTrue(vm.menu().canLoadMore)
        coVerify(exactly = 0) { dismissalTracker.onCompleted(any()) }
        verify(exactly = 0) { widgetRefresher.refresh() }
    }

    @Test
    fun `a failed completion write on the last habit does not leave the screen loading`() = runTest(testDispatcher) {
        val habits = listOf(habit(1L))
        givenHabits(habits, allAvailable(habits))
        coEvery { triggerRepository.insert(any()) } throws IllegalStateException("disk full")
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.complete(1L)
        assertEquals(NowMenuUiState.Loading, vm.uiState.value)
        advanceUntilIdle()

        assertEquals(listOf(1L), vm.menu().items.map { it.habitId })
        coVerify(exactly = 1) { availabilityService.computeForAll(habits) }
    }

    @Test
    fun `a failed promotion after the write keeps the habit completed`() = runTest(testDispatcher) {
        val habits = (1L..4L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        coEvery { triggerRepository.insert(any()) } returns 99L
        coEvery { dismissalTracker.onCompleted(99L) } throws IllegalStateException("promotion broke")
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()
        val completedId = vm.menu().items.first().habitId

        vm.complete(completedId)
        advanceUntilIdle()

        assertFalse(completedId in vm.menu().items.map { it.habitId })
        coVerifyOrder {
            triggerRepository.insert(any())
            dismissalTracker.onCompleted(99L)
        }
    }

    @Test
    fun `a failed promotion skips consuming the displayed variant`() = runTest(testDispatcher) {
        val habits = (1L..2L).map { habit(it) }
        givenHabits(habits, allAvailable(habits))
        coEvery { variationRepository.peekUnusedVariation(1L) } returns variation(10L, 1L)
        coEvery { triggerRepository.insert(any()) } returns 99L
        coEvery { dismissalTracker.onCompleted(99L) } throws IllegalStateException("promotion broke")
        val vm = buildViewModel()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(10L, vm.menu().items.single { it.habitId == 1L }.variationId)

        vm.complete(1L)
        advanceUntilIdle()

        assertFalse(1L in vm.menu().items.map { it.habitId })
        coVerify(exactly = 1) { dismissalTracker.onCompleted(99L) }
        coVerify(exactly = 0) { variationRepository.markConsumed(any()) }
    }

    @Test
    fun `days with any completion is forwarded from the trigger repository`() = runTest(testDispatcher) {
        every { triggerRepository.daysWithAnyCompletion() } returns flowOf(7)
        val vm = buildViewModel()
        backgroundScope.launch { vm.daysWithAnyCompletion.collect() }
        advanceUntilIdle()

        assertEquals(7, vm.daysWithAnyCompletion.value)
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
