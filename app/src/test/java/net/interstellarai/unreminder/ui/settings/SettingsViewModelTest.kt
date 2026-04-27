package net.interstellarai.unreminder.ui.settings

import android.app.AlarmManager
import android.content.Context
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.trigger.TriggerPipeline
import androidx.work.WorkManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var triggerRepository: TriggerRepository
    private lateinit var triggerPipeline: TriggerPipeline
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var viewModel: SettingsViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        triggerRepository = mockk(relaxUnitFun = true)
        triggerPipeline = mockk(relaxUnitFun = true)
        context = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        every { context.getSystemService(Context.ALARM_SERVICE) } returns mockk<AlarmManager>(relaxed = true)

        viewModel = SettingsViewModel(
            context = context,
            triggerPipeline = triggerPipeline,
            triggerRepository = triggerRepository,
            workManager = workManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `testTriggerNow inserts trigger with null source and executes pipeline`() = runTest {
        coEvery { triggerRepository.insert(any()) } returns 1L
        viewModel.testTriggerNow()
        advanceUntilIdle()
        coVerify {
            triggerRepository.insert(match { trigger ->
                trigger.source == null && trigger.status == TriggerStatus.SCHEDULED
            })
        }
        coVerify { triggerPipeline.execute(1L) }
    }

    @Test
    fun `testTriggerNow sets testTriggered true in uiState`() = runTest {
        coEvery { triggerRepository.insert(any()) } returns 1L
        viewModel.testTriggerNow()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.testTriggered)
    }

    @Test
    fun `clearError sets errorMessage to null`() {
        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `regenerateTriggers deletes all scheduled triggers and enqueues next worker`() = runTest {
        viewModel.regenerateTriggers()
        advanceUntilIdle()
        coVerify { triggerRepository.deleteAllScheduled() }
        coVerify { workManager.enqueueUniqueWork(any(), any(), any<androidx.work.OneTimeWorkRequest>()) }
    }
}
