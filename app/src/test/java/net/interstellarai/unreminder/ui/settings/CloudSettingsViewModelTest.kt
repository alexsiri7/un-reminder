package net.interstellarai.unreminder.ui.settings

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.service.worker.RefillScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockVariationRepository: VariationRepository = mockk(relaxUnitFun = true)
    private val mockRefillScheduler: RefillScheduler = mockk(relaxUnitFun = true)
    private val mockHabitRepository: HabitRepository = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CloudSettingsViewModel =
        CloudSettingsViewModel(
            mockVariationRepository,
            mockRefillScheduler,
            mockHabitRepository,
        )

    @Test
    fun `regenerateAll deletes pool and enqueues refill for each active habit`() = runTest(testDispatcher) {
        val habits = listOf(
            HabitEntity(id = 1L, name = "A"),
            HabitEntity(id = 2L, name = "B"),
        )
        coEvery { mockHabitRepository.getAllActive() } returns flowOf(habits)

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockVariationRepository.deleteForHabit(1L) }
        coVerify(exactly = 1) { mockVariationRepository.deleteForHabit(2L) }
        coVerify(exactly = 1) { mockRefillScheduler.enqueueForHabit(1L) }
        coVerify(exactly = 1) { mockRefillScheduler.enqueueForHabit(2L) }
    }

    @Test
    fun `regenerateAll shows success message when all habits succeed`() = runTest(testDispatcher) {
        val habits = listOf(
            HabitEntity(id = 1L, name = "A"),
            HabitEntity(id = 2L, name = "B"),
        )
        coEvery { mockHabitRepository.getAllActive() } returns flowOf(habits)

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        assertEquals("Queued regeneration for 2 habit(s).", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isRegenerating)
    }

    @Test
    fun `regenerateAll reports partial failure count`() = runTest(testDispatcher) {
        val habits = listOf(
            HabitEntity(id = 1L, name = "A"),
            HabitEntity(id = 2L, name = "B"),
        )
        coEvery { mockHabitRepository.getAllActive() } returns flowOf(habits)
        coEvery { mockVariationRepository.deleteForHabit(1L) } throws RuntimeException("db error")

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        assertEquals("Failed to regenerate 1 variant(s).", vm.uiState.value.errorMessage)
        coVerify(exactly = 1) { mockRefillScheduler.enqueueForHabit(2L) }
        assertFalse(vm.uiState.value.isRegenerating)
    }

    @Test
    fun `regenerateAll sets isRegenerating to false after exception`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getAllActive() } throws RuntimeException("db error")
        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRegenerating)
    }

    @Test
    fun `regenerateAll handles empty habit list gracefully`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getAllActive() } returns flowOf(emptyList())

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockVariationRepository.deleteForHabit(any()) }
        coVerify(exactly = 0) { mockRefillScheduler.enqueueForHabit(any()) }
    }

    @Test
    fun `regenerateAll sets errorMessage on exception`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getAllActive() } throws RuntimeException("db error")
        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()
        assertEquals("Failed to regenerate variants.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `clearError nullifies errorMessage`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getAllActive() } throws RuntimeException("boom")
        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.errorMessage)
        vm.clearError()
        assertNull(vm.uiState.value.errorMessage)
    }
}
