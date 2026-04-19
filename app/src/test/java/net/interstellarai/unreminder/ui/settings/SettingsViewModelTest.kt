package net.interstellarai.unreminder.ui.settings

import android.app.AlarmManager
import android.content.Context
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.ActiveModelRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
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
        context = mockk(relaxed = true)
        every { context.getSystemService(Context.ALARM_SERVICE) } returns mockk<AlarmManager>(relaxed = true)

        viewModel = SettingsViewModel(
            context = context,
            triggerPipeline = triggerPipeline,
            triggerRepository = triggerRepository,
            activeModelRepository = activeModelRepository,
            promptGenerator = promptGenerator,
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
}
