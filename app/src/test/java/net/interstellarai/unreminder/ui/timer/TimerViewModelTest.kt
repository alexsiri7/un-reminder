package net.interstellarai.unreminder.ui.timer

import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import java.time.Instant
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {

    private lateinit var triggerRepository: TriggerRepository
    private lateinit var dismissalTracker: DismissalTracker
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var viewModel: TimerViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        triggerRepository = mockk(relaxUnitFun = true)
        dismissalTracker = mockk(relaxUnitFun = true)
        notificationHelper = mockk(relaxUnitFun = true)
        viewModel = TimerViewModel(triggerRepository, dismissalTracker, notificationHelper, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeTrigger(prompt: String) = TriggerEntity(
        id = 42L,
        habitId = 1L,
        scheduledAt = Instant.EPOCH,
        status = TriggerStatus.SCHEDULED,
        generatedPrompt = prompt,
    )

    @Test
    fun `init loads prompt and detects duration`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger("meditate for 5 min")
        viewModel.init(42L)
        advanceUntilIdle()
        assertEquals(300, viewModel.uiState.value.totalSeconds)
        assertEquals("meditate for 5 min", viewModel.uiState.value.promptText)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `init with no duration sets totalSeconds null`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger("just do it")
        viewModel.init(42L)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.totalSeconds)
    }

    @Test
    fun `init with null trigger sets promptText empty`() = runTest {
        coEvery { triggerRepository.getById(99L) } returns null
        viewModel.init(99L)
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.promptText)
        assertNull(viewModel.uiState.value.totalSeconds)
    }

    @Test
    fun `start sets isRunning true`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger("hold for 10 seconds")
        viewModel.init(42L)
        advanceUntilIdle()
        viewModel.start()
        advanceTimeBy(500)
        assertTrue(viewModel.uiState.value.isRunning)
    }

    @Test
    fun `start counts down to isFinished`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger("hold for 2 seconds")
        viewModel.init(42L)
        advanceUntilIdle()
        viewModel.start()
        advanceTimeBy(2_001)
        assertEquals(0, viewModel.uiState.value.remainingSeconds)
        assertTrue(viewModel.uiState.value.isFinished)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun `pause stops countdown and preserves remaining`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger("hold for 60 seconds")
        viewModel.init(42L)
        advanceUntilIdle()
        viewModel.start()
        advanceTimeBy(10_001)
        viewModel.pause()
        val remaining = viewModel.uiState.value.remainingSeconds!!
        advanceTimeBy(10_000)
        assertEquals(remaining, viewModel.uiState.value.remainingSeconds)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun `reset restores totalSeconds`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger("hold for 30 seconds")
        viewModel.init(42L)
        advanceUntilIdle()
        viewModel.start()
        advanceTimeBy(5_001)
        viewModel.reset()
        assertEquals(30, viewModel.uiState.value.remainingSeconds)
        assertFalse(viewModel.uiState.value.isRunning)
        assertFalse(viewModel.uiState.value.isFinished)
    }

    @Test
    fun `markCompleted records COMPLETED outcome and sets isDone`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger("do it for 5 min")
        viewModel.init(42L)
        advanceUntilIdle()
        viewModel.markCompleted()
        advanceUntilIdle()
        coVerify { triggerRepository.updateOutcome(42L, TriggerStatus.COMPLETED) }
        coVerify { dismissalTracker.onCompleted(42L) }
        assertTrue(viewModel.uiState.value.isDone)
    }

    @Test
    fun `markDismissed records DISMISSED outcome and sets isDone`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger("do it for 5 min")
        viewModel.init(42L)
        advanceUntilIdle()
        viewModel.markDismissed()
        advanceUntilIdle()
        coVerify { triggerRepository.updateOutcome(42L, TriggerStatus.DISMISSED) }
        coVerify { dismissalTracker.onDismissed(42L) }
        assertTrue(viewModel.uiState.value.isDone)
    }

    @Test
    fun `markCompleted cancels notification`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger("do it for 5 min")
        viewModel.init(42L)
        advanceUntilIdle()
        viewModel.markCompleted()
        advanceUntilIdle()
        coVerify { notificationHelper.cancelNotification(42L) }
    }
}
