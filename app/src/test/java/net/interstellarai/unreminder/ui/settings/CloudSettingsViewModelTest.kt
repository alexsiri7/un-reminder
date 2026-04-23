package net.interstellarai.unreminder.ui.settings

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.data.repository.WorkerSettingsRepository
import net.interstellarai.unreminder.service.worker.RefillScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockWorkerSettings: WorkerSettingsRepository = mockk(relaxUnitFun = true)
    private val mockVariationRepository: VariationRepository = mockk(relaxUnitFun = true)
    private val mockRefillScheduler: RefillScheduler = mockk(relaxUnitFun = true)
    private val mockHabitRepository: HabitRepository = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockWorkerSettings.workerUrl } returns flowOf("https://worker.test")
        coEvery { mockWorkerSettings.workerSecret } returns flowOf("secret123")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CloudSettingsViewModel =
        CloudSettingsViewModel(
            mockWorkerSettings,
            mockVariationRepository,
            mockRefillScheduler,
            mockHabitRepository,
        )

    @Test
    fun `init emits persisted workerUrl from repository`() = runTest(testDispatcher) {
        val vm = createViewModel()
        val collected = mutableListOf<String>()
        val job = launch { vm.workerUrl.collect { collected.add(it) } }
        advanceUntilIdle()
        assertEquals("https://worker.test", collected.last())
        job.cancel()
    }

    @Test
    fun `setWorkerUrl persists to repository`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.setWorkerUrl("https://new-url.test")
        advanceUntilIdle()
        coVerify { mockWorkerSettings.setWorkerUrl("https://new-url.test") }
    }

    @Test
    fun `setWorkerSecret persists to repository`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.setWorkerSecret("new-secret")
        advanceUntilIdle()
        coVerify { mockWorkerSettings.setWorkerSecret("new-secret") }
    }

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

    // --- Error paths ---

    @Test
    fun `setWorkerUrl sets errorMessage on exception`() = runTest(testDispatcher) {
        coEvery { mockWorkerSettings.setWorkerUrl(any()) } throws RuntimeException("disk full")
        val vm = createViewModel()
        vm.setWorkerUrl("https://fail.test")
        advanceUntilIdle()
        assertEquals("Failed to save worker URL.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `setWorkerSecret sets errorMessage on exception`() = runTest(testDispatcher) {
        coEvery { mockWorkerSettings.setWorkerSecret(any()) } throws RuntimeException("disk full")
        val vm = createViewModel()
        vm.setWorkerSecret("bad-secret")
        advanceUntilIdle()
        assertEquals("Failed to save worker secret.", vm.uiState.value.errorMessage)
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
        coEvery { mockWorkerSettings.setWorkerUrl(any()) } throws RuntimeException("boom")
        val vm = createViewModel()
        vm.setWorkerUrl("x")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.errorMessage)
        vm.clearError()
        assertNull(vm.uiState.value.errorMessage)
    }
}
