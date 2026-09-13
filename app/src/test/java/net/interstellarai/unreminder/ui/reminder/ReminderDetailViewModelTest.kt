package net.interstellarai.unreminder.ui.reminder

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.TriggerEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import net.interstellarai.unreminder.widget.WidgetRefresher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var triggerRepository: TriggerRepository
    private lateinit var habitRepository: HabitRepository
    private lateinit var dismissalTracker: DismissalTracker
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var widgetRefresher: WidgetRefresher
    private lateinit var viewModel: ReminderDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        triggerRepository = mockk(relaxUnitFun = true)
        habitRepository = mockk(relaxUnitFun = true)
        dismissalTracker = mockk(relaxUnitFun = true)
        notificationHelper = mockk(relaxUnitFun = true)
        widgetRefresher = mockk(relaxUnitFun = true)
        coEvery { triggerRepository.recordOutcome(any(), any()) } returns true
        viewModel = ReminderDetailViewModel(
            triggerRepository, habitRepository, dismissalTracker, notificationHelper, widgetRefresher, testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeTrigger(
        habitId: Long = 1L,
        prompt: String = "test prompt",
        actionUrl: String? = null,
        status: TriggerStatus = TriggerStatus.SCHEDULED,
    ) = TriggerEntity(
        id = 42L,
        habitId = habitId,
        scheduledAt = Instant.EPOCH,
        status = status,
        generatedPrompt = prompt,
        actionUrl = actionUrl,
    )

    private fun makeHabit(id: Long = 1L, name: String = "Meditate", level: Int = 3) =
        HabitEntity(id = id, name = name, dedicationLevel = level)

    @Test
    fun `init loads prompt and habit name into uiState`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger(prompt = "breathe slowly")
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit(name = "Meditation")
        viewModel.init(42L)
        advanceUntilIdle()
        assertEquals("breathe slowly", viewModel.uiState.value.promptText)
        assertEquals("Meditation", viewModel.uiState.value.habitName)
        assertEquals(3, viewModel.uiState.value.dedicationLevel)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `init with null trigger sets empty promptText and isLoading false`() = runTest {
        coEvery { triggerRepository.getById(99L) } returns null
        viewModel.init(99L)
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.promptText)
        assertEquals("", viewModel.uiState.value.habitName)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `init surfaces the trigger's action_url as videoUrl`() = runTest {
        val url = "https://www.youtube.com/results?search_query=C+major+vocal+scale"
        coEvery { triggerRepository.getById(42L) } returns makeTrigger(actionUrl = url)
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        viewModel.init(42L)
        advanceUntilIdle()
        assertEquals(url, viewModel.uiState.value.videoUrl)
    }

    @Test
    fun `init hides a missing action_url`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger(actionUrl = null)
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        viewModel.init(42L)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.videoUrl)
    }

    @Test
    fun `init hides a non-https action_url`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger(actionUrl = "http://example.com/video")
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        viewModel.init(42L)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.videoUrl)
    }

    @Test
    fun `init records no completion on a trigger that is not live`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger()
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        viewModel.init(42L)
        advanceUntilIdle()
        coVerify(exactly = 0) { triggerRepository.recordOutcome(any(), any()) }
        coVerify(exactly = 0) { dismissalTracker.onCompleted(any()) }
        coVerify(exactly = 0) { dismissalTracker.onDismissed(any()) }
    }

    @Test
    fun `init on a FIRED trigger records OPENED, cancels the notification and touches no dedication level`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger(status = TriggerStatus.FIRED)
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        viewModel.init(42L)
        advanceUntilIdle()
        coVerify(exactly = 1) { triggerRepository.recordOutcome(42L, TriggerStatus.OPENED) }
        verify(exactly = 1) { notificationHelper.cancelNotification(42L) }
        coVerify(exactly = 0) { dismissalTracker.onDismissed(any()) }
        coVerify(exactly = 0) { dismissalTracker.onCompleted(any()) }
        assertTrue(viewModel.uiState.value.canComplete)
    }

    @Test
    fun `init whose OPENED write is declined hides Did it`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger(status = TriggerStatus.FIRED)
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        coEvery { triggerRepository.recordOutcome(42L, TriggerStatus.OPENED) } returns false
        viewModel.init(42L)
        advanceUntilIdle()
        verify(exactly = 1) { notificationHelper.cancelNotification(42L) }
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.canComplete)
    }

    @Test
    fun `init on a resolved trigger records nothing and hides Did it`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger(status = TriggerStatus.DISMISSED)
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        viewModel.init(42L)
        advanceUntilIdle()
        coVerify(exactly = 0) { triggerRepository.recordOutcome(any(), any()) }
        verify(exactly = 0) { notificationHelper.cancelNotification(any()) }
        assertFalse(viewModel.uiState.value.canComplete)
    }

    @Test
    fun `markCompleted records COMPLETED outcome and sets isDone`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger(status = TriggerStatus.FIRED)
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        viewModel.init(42L)
        advanceUntilIdle()
        viewModel.markCompleted()
        advanceUntilIdle()
        coVerify(exactly = 1) { triggerRepository.recordOutcome(42L, TriggerStatus.COMPLETED) }
        coVerify(exactly = 1) { dismissalTracker.onCompleted(42L) }
        coVerify { notificationHelper.cancelNotification(42L) }
        verify(exactly = 1) { widgetRefresher.refresh() }
        assertTrue(viewModel.uiState.value.isDone)
    }

    @Test
    fun `markCompleted after an open upgrades OPENED to COMPLETED`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger(status = TriggerStatus.OPENED)
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        viewModel.init(42L)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canComplete)
        viewModel.markCompleted()
        advanceUntilIdle()
        coVerify(exactly = 0) { triggerRepository.recordOutcome(42L, TriggerStatus.OPENED) }
        coVerify(exactly = 1) { triggerRepository.recordOutcome(42L, TriggerStatus.COMPLETED) }
        coVerify(exactly = 1) { dismissalTracker.onCompleted(42L) }
        assertTrue(viewModel.uiState.value.isDone)
    }

    @Test
    fun `markCompleted whose write is declined runs no side effects, stays on screen and hides Did it`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger(status = TriggerStatus.OPENED)
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        coEvery { triggerRepository.recordOutcome(42L, TriggerStatus.COMPLETED) } returns false
        viewModel.init(42L)
        advanceUntilIdle()
        viewModel.markCompleted()
        advanceUntilIdle()
        coVerify(exactly = 0) { dismissalTracker.onCompleted(any()) }
        verify(exactly = 0) { widgetRefresher.refresh() }
        verify(exactly = 1) { notificationHelper.cancelNotification(42L) }
        assertFalse(viewModel.uiState.value.isDone)
        assertFalse(viewModel.uiState.value.canComplete)
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun `markCompleted before init completes does not record outcome for invalid id`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger()
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        // Do NOT advance — init coroutine is still in-flight; triggerId is still -1L
        viewModel.init(42L)
        viewModel.markCompleted()
        advanceUntilIdle()
        // With the -1L guard, recordOutcome should never be called with -1L
        coVerify(exactly = 0) { triggerRepository.recordOutcome(-1L, any()) }
    }
}
