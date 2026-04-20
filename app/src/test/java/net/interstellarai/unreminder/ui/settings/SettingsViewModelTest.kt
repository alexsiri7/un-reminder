package net.interstellarai.unreminder.ui.settings

import android.app.AlarmManager
import android.content.Context
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.ActiveModelRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.WorkerSettingsRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.ModelCatalog
import net.interstellarai.unreminder.service.llm.PromptGenerator
import net.interstellarai.unreminder.service.trigger.TriggerPipeline
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var triggerRepository: TriggerRepository
    private lateinit var triggerPipeline: TriggerPipeline
    private lateinit var activeModelRepository: ActiveModelRepository
    private lateinit var promptGenerator: PromptGenerator
    private lateinit var workerSettingsRepository: WorkerSettingsRepository
    private lateinit var context: Context
    private lateinit var viewModel: SettingsViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        triggerRepository = mockk(relaxUnitFun = true)
        triggerPipeline = mockk(relaxUnitFun = true)
        activeModelRepository = mockk(relaxed = true) {
            every { active } returns flowOf(ModelCatalog.default)
        }
        promptGenerator = mockk(relaxed = true) {
            every { aiStatus } returns MutableStateFlow<AiStatus>(AiStatus.Idle)
            every { downloadProgress } returns MutableStateFlow<Float?>(null)
        }
        workerSettingsRepository = mockk(relaxed = true) {
            every { workerUrl } returns flowOf("")
            every { workerSecret } returns flowOf("")
        }
        context = mockk(relaxed = true)
        every { context.getSystemService(Context.ALARM_SERVICE) } returns mockk<AlarmManager>(relaxed = true)

        viewModel = SettingsViewModel(
            context = context,
            triggerPipeline = triggerPipeline,
            triggerRepository = triggerRepository,
            activeModelRepository = activeModelRepository,
            promptGenerator = promptGenerator,
            workerSettingsRepository = workerSettingsRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `surpriseMe inserts trigger with MANUAL source and null windowId`() = runTest {
        coEvery { triggerRepository.insert(any()) } returns 1L

        viewModel.surpriseMe()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            triggerRepository.insert(match { trigger ->
                trigger.source == "MANUAL" &&
                trigger.windowId == null &&
                trigger.status == TriggerStatus.SCHEDULED
            })
        }
    }

    @Test
    fun `surpriseMe executes pipeline with inserted trigger id`() = runTest {
        val triggerId = 7L
        coEvery { triggerRepository.insert(any()) } returns triggerId

        viewModel.surpriseMe()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { triggerPipeline.execute(triggerId) }
    }

    @Test
    fun `setWorkerUrl delegates to repository`() = runTest {
        coEvery { workerSettingsRepository.setWorkerUrl(any()) } returns Unit

        viewModel.setWorkerUrl("https://example.com")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { workerSettingsRepository.setWorkerUrl("https://example.com") }
    }

    @Test
    fun `setWorkerSecret delegates to repository`() = runTest {
        coEvery { workerSettingsRepository.setWorkerSecret(any()) } returns Unit

        viewModel.setWorkerSecret("s3cret")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { workerSettingsRepository.setWorkerSecret("s3cret") }
    }
}
