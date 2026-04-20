package net.interstellarai.unreminder.ui.habit

import net.interstellarai.unreminder.data.repository.FeatureFlagsRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.WorkerSettingsRepository
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.PromptGenerator
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockPromptGenerator: PromptGenerator = mockk()
    private val mockHabitRepository: HabitRepository = mockk()
    private val mockFeatureFlags: FeatureFlagsRepository = mockk()
    private val mockWorkerSettings: WorkerSettingsRepository = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { mockHabitRepository.getAll() } returns flowOf(emptyList())
        every { mockPromptGenerator.downloadProgress } returns MutableStateFlow(null)
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow(AiStatus.Ready)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = HabitListViewModel(
        mockHabitRepository, mockPromptGenerator, mockFeatureFlags, mockWorkerSettings
    )

    @Test
    fun `aiStatus is Unavailable when cloud ON but url and secret blank`() = runTest(testDispatcher) {
        every { mockFeatureFlags.useCloudPool } returns flowOf(true)
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf("")
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf("")
        val vm = buildViewModel()
        val job = launch { vm.aiStatus.collect {} }
        advanceUntilIdle()
        assertEquals(AiStatus.Unavailable, vm.aiStatus.value)
        job.cancel()
    }

    @Test
    fun `aiStatus is Unavailable when cloud ON and url blank but secret non-blank`() = runTest(testDispatcher) {
        every { mockFeatureFlags.useCloudPool } returns flowOf(true)
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf("")
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf("secret")
        val vm = buildViewModel()
        val job = launch { vm.aiStatus.collect {} }
        advanceUntilIdle()
        assertEquals(AiStatus.Unavailable, vm.aiStatus.value)
        job.cancel()
    }

    @Test
    fun `aiStatus is Unavailable when cloud ON and secret blank but url non-blank`() = runTest(testDispatcher) {
        every { mockFeatureFlags.useCloudPool } returns flowOf(true)
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf("")
        val vm = buildViewModel()
        val job = launch { vm.aiStatus.collect {} }
        advanceUntilIdle()
        assertEquals(AiStatus.Unavailable, vm.aiStatus.value)
        job.cancel()
    }

    @Test
    fun `aiStatus is Ready when cloud ON and url non-blank`() = runTest(testDispatcher) {
        every { mockFeatureFlags.useCloudPool } returns flowOf(true)
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf("secret")
        val vm = buildViewModel()
        val job = launch { vm.aiStatus.collect {} }
        advanceUntilIdle()
        assertEquals(AiStatus.Ready, vm.aiStatus.value)
        job.cancel()
    }

    @Test
    fun `aiStatus delegates to promptGenerator when cloud OFF`() = runTest(testDispatcher) {
        every { mockFeatureFlags.useCloudPool } returns flowOf(false)
        every { mockWorkerSettings.effectiveWorkerUrl } returns flowOf("")
        every { mockWorkerSettings.effectiveWorkerSecret } returns flowOf("")
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow(AiStatus.Failed)
        val vm = buildViewModel()
        val job = launch { vm.aiStatus.collect {} }
        advanceUntilIdle()
        assertEquals(AiStatus.Failed, vm.aiStatus.value)
        job.cancel()
    }
}
