package net.interstellarai.unreminder.ui.recent

import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.worker.RandomIntervalWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RecentTriggersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val triggerRepository: TriggerRepository = mockk()
    private val habitRepository: HabitRepository = mockk()
    private val workManager: WorkManager = mockk()
    private val workInfos = MutableStateFlow<List<WorkInfo>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { triggerRepository.getRecentTriggers(20) } returns flowOf(emptyList())
        every { habitRepository.getAll() } returns flowOf(emptyList())
        every {
            workManager.getWorkInfosForUniqueWorkFlow(RandomIntervalWorker.WORK_NAME)
        } returns workInfos
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        RecentTriggersViewModel(triggerRepository, habitRepository, workManager)

    private fun mockInfo(state: WorkInfo.State, nextMillis: Long): WorkInfo = mockk {
        every { this@mockk.state } returns state
        every { this@mockk.nextScheduleTimeMillis } returns nextMillis
    }

    @Test
    fun `empty WorkInfo list maps to NotScheduled`() = runTest(testDispatcher) {
        workInfos.value = emptyList()
        val vm = buildViewModel()
        backgroundScope.launch { vm.nextTrigger.collect {} }
        advanceUntilIdle()
        assertEquals(NextTriggerState.NotScheduled, vm.nextTrigger.value)
    }

    @Test
    fun `ENQUEUED with finite next millis maps to Scheduled`() = runTest(testDispatcher) {
        val millis = 1_700_000_000_000L
        workInfos.value = listOf(mockInfo(WorkInfo.State.ENQUEUED, millis))
        val vm = buildViewModel()
        backgroundScope.launch { vm.nextTrigger.collect {} }
        advanceUntilIdle()
        assertEquals(
            NextTriggerState.Scheduled(Instant.ofEpochMilli(millis)),
            vm.nextTrigger.value,
        )
    }

    @Test
    fun `ENQUEUED with Long MAX_VALUE next millis maps to NotScheduled`() = runTest(testDispatcher) {
        workInfos.value = listOf(mockInfo(WorkInfo.State.ENQUEUED, Long.MAX_VALUE))
        val vm = buildViewModel()
        backgroundScope.launch { vm.nextTrigger.collect {} }
        advanceUntilIdle()
        assertEquals(NextTriggerState.NotScheduled, vm.nextTrigger.value)
    }

    @Test
    fun `RUNNING-only list maps to NotScheduled`() = runTest(testDispatcher) {
        workInfos.value = listOf(mockInfo(WorkInfo.State.RUNNING, Long.MAX_VALUE))
        val vm = buildViewModel()
        backgroundScope.launch { vm.nextTrigger.collect {} }
        advanceUntilIdle()
        assertEquals(NextTriggerState.NotScheduled, vm.nextTrigger.value)
    }

    @Test
    fun `mixed CANCELLED and ENQUEUED reads the ENQUEUED one`() = runTest(testDispatcher) {
        val millis = 1_700_000_500_000L
        workInfos.value = listOf(
            mockInfo(WorkInfo.State.CANCELLED, Long.MAX_VALUE),
            mockInfo(WorkInfo.State.ENQUEUED, millis),
        )
        val vm = buildViewModel()
        backgroundScope.launch { vm.nextTrigger.collect {} }
        advanceUntilIdle()
        assertEquals(
            NextTriggerState.Scheduled(Instant.ofEpochMilli(millis)),
            vm.nextTrigger.value,
        )
    }
}
