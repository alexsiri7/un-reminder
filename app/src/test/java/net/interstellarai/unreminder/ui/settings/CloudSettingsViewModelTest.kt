package net.interstellarai.unreminder.ui.settings

import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.service.worker.RefillScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockRefillScheduler: RefillScheduler = mockk()
    private val mockHabitRepository: HabitRepository = mockk()
    private val workManager: WorkManager = mockk()
    private val workInfos = MutableStateFlow<List<WorkInfo>>(emptyList())

    private val habits = listOf(
        HabitEntity(id = 1L, name = "A"),
        HabitEntity(id = 2L, name = "B"),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { mockRefillScheduler.enqueueRegenerate(any()) } answers { UUID.randomUUID() }
        every { workManager.getWorkInfosFlow(any()) } returns workInfos
        coEvery { mockHabitRepository.getAllActive() } returns flowOf(habits)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CloudSettingsViewModel =
        CloudSettingsViewModel(mockRefillScheduler, mockHabitRepository, workManager)

    private fun mockInfo(state: WorkInfo.State): WorkInfo = mockk {
        every { this@mockk.state } returns state
    }

    private fun settle(vararg states: WorkInfo.State) {
        workInfos.value = states.map(::mockInfo)
    }

    @Test
    fun `regenerateAll enqueues a regeneration for each active habit and observes exactly those works`() = runTest(testDispatcher) {
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())
        every { mockRefillScheduler.enqueueRegenerate(1L) } returns ids[0]
        every { mockRefillScheduler.enqueueRegenerate(2L) } returns ids[1]
        val query = slot<WorkQuery>()

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        verify(exactly = 1) { mockRefillScheduler.enqueueRegenerate(1L) }
        verify(exactly = 1) { mockRefillScheduler.enqueueRegenerate(2L) }
        verify(exactly = 1) { workManager.getWorkInfosFlow(capture(query)) }
        assertEquals(ids, query.captured.ids)
        assertEquals(RegenerationProgress(total = 2, done = 0, failed = 0), vm.uiState.value.regeneration)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `regenerateAll shows in-flight progress until every work finishes, then a summary`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        settle(WorkInfo.State.SUCCEEDED, WorkInfo.State.RUNNING)
        advanceUntilIdle()
        assertEquals(RegenerationProgress(total = 2, done = 1, failed = 0), vm.uiState.value.regeneration)
        assertNull(vm.uiState.value.errorMessage)

        settle(WorkInfo.State.SUCCEEDED, WorkInfo.State.SUCCEEDED)
        advanceUntilIdle()
        assertNull(vm.uiState.value.regeneration)
        assertEquals("Regenerated 2 habit(s).", vm.uiState.value.errorMessage)
    }

    @Test
    fun `a failed work counts as failed in the summary`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        settle(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED)
        advanceUntilIdle()

        assertNull(vm.uiState.value.regeneration)
        assertEquals("Regenerated 1 habit(s), 1 failed — previous variants kept.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `a cancelled work counts as failed in the summary`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        settle(WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED)
        advanceUntilIdle()

        assertNull(vm.uiState.value.regeneration)
        assertEquals("Regenerated 1 habit(s), 1 failed — previous variants kept.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `a retrying work is still in flight`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        settle(WorkInfo.State.SUCCEEDED, WorkInfo.State.ENQUEUED)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.regeneration?.inFlight)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `the summary fires once even if WorkManager emits again afterwards`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()
        settle(WorkInfo.State.SUCCEEDED, WorkInfo.State.SUCCEEDED)
        advanceUntilIdle()
        vm.clearError()

        settle()
        advanceUntilIdle()

        assertNull(vm.uiState.value.regeneration)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `regenerateAll is a no-op while a regeneration is outstanding`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        vm.regenerateAll()
        advanceUntilIdle()

        verify(exactly = 1) { mockRefillScheduler.enqueueRegenerate(1L) }
        verify(exactly = 1) { mockRefillScheduler.enqueueRegenerate(2L) }
    }

    @Test
    fun `two presses before the first has enqueued anything start a single regeneration`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.regenerateAll()
        vm.regenerateAll()
        advanceUntilIdle()

        verify(exactly = 1) { mockRefillScheduler.enqueueRegenerate(1L) }
        verify(exactly = 1) { mockRefillScheduler.enqueueRegenerate(2L) }
        verify(exactly = 1) { workManager.getWorkInfosFlow(any()) }
    }

    @Test
    fun `a broken progress flow re-enables the button instead of leaving it stuck`() = runTest(testDispatcher) {
        every { workManager.getWorkInfosFlow(any()) } returns flow { throw IllegalStateException("boom") }

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        assertNull(vm.uiState.value.regeneration)
        assertNotNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `regenerateAll with no active habits reports it and observes nothing`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getAllActive() } returns flowOf(emptyList())

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        assertEquals("No active habits to regenerate.", vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.regeneration)
        verify(exactly = 0) { mockRefillScheduler.enqueueRegenerate(any()) }
        verify(exactly = 0) { workManager.getWorkInfosFlow(any()) }
    }

    @Test
    fun `regenerateAll reports habits whose enqueue threw and still observes the rest`() = runTest(testDispatcher) {
        every { mockRefillScheduler.enqueueRegenerate(1L) } throws RuntimeException("wm error")
        val query = slot<WorkQuery>()

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        assertEquals("Failed to queue regeneration for 1 habit(s).", vm.uiState.value.errorMessage)
        verify(exactly = 1) { workManager.getWorkInfosFlow(capture(query)) }
        assertEquals(1, query.captured.ids.size)
        assertEquals(RegenerationProgress(total = 1, done = 0, failed = 0), vm.uiState.value.regeneration)
    }

    @Test
    fun `the summary still reports habits whose enqueue threw after their notice was replaced`() = runTest(testDispatcher) {
        every { mockRefillScheduler.enqueueRegenerate(1L) } throws RuntimeException("wm error")

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        settle(WorkInfo.State.SUCCEEDED)
        advanceUntilIdle()

        assertNull(vm.uiState.value.regeneration)
        assertEquals("Regenerated 1 habit(s), 1 not queued — previous variants kept.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `the summary lists failed and not-queued habits together`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getAllActive() } returns flowOf(habits + HabitEntity(id = 3L, name = "C"))
        every { mockRefillScheduler.enqueueRegenerate(3L) } throws RuntimeException("wm error")

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        settle(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED)
        advanceUntilIdle()

        assertEquals(
            "Regenerated 1 habit(s), 1 failed and 1 not queued — previous variants kept.",
            vm.uiState.value.errorMessage,
        )
    }

    @Test
    fun `a broken progress flow still reports habits whose enqueue threw`() = runTest(testDispatcher) {
        every { mockRefillScheduler.enqueueRegenerate(1L) } throws RuntimeException("wm error")
        every { workManager.getWorkInfosFlow(any()) } returns flow { throw IllegalStateException("boom") }

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        assertNull(vm.uiState.value.regeneration)
        assertEquals(
            "Regeneration is still running in the background. 1 habit(s) not queued.",
            vm.uiState.value.errorMessage,
        )
    }

    @Test
    fun `regenerateAll reports every habit when no enqueue succeeds and leaves the button enabled`() = runTest(testDispatcher) {
        every { mockRefillScheduler.enqueueRegenerate(any()) } throws RuntimeException("wm error")

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        assertEquals("Failed to queue regeneration for 2 habit(s).", vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.regeneration)
        verify(exactly = 0) { workManager.getWorkInfosFlow(any()) }
    }

    @Test
    fun `regenerateAll sets errorMessage on exception`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getAllActive() } throws RuntimeException("db error")

        val vm = createViewModel()
        vm.regenerateAll()
        advanceUntilIdle()

        assertEquals("Failed to regenerate variants.", vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.regeneration)
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
