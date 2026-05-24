package net.interstellarai.unreminder.ui.reminder

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private lateinit var viewModel: ReminderDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        triggerRepository = mockk(relaxUnitFun = true)
        habitRepository = mockk(relaxUnitFun = true)
        dismissalTracker = mockk(relaxUnitFun = true)
        notificationHelper = mockk(relaxUnitFun = true)
        viewModel = ReminderDetailViewModel(
            triggerRepository, habitRepository, dismissalTracker, notificationHelper, testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeTrigger(habitId: Long = 1L, prompt: String = "test prompt") = TriggerEntity(
        id = 42L,
        habitId = habitId,
        scheduledAt = Instant.EPOCH,
        status = TriggerStatus.SCHEDULED,
        generatedPrompt = prompt,
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
    fun `markCompleted records COMPLETED outcome and sets isDone`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger()
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        viewModel.init(42L)
        advanceUntilIdle()
        viewModel.markCompleted()
        advanceUntilIdle()
        coVerify { triggerRepository.updateOutcome(42L, TriggerStatus.COMPLETED) }
        coVerify { dismissalTracker.onCompleted(42L) }
        coVerify { notificationHelper.cancelNotification(42L) }
        assertTrue(viewModel.uiState.value.isDone)
    }

    @Test
    fun `markDismissed records DISMISSED outcome and sets isDone`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger()
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        viewModel.init(42L)
        advanceUntilIdle()
        viewModel.markDismissed()
        advanceUntilIdle()
        coVerify { triggerRepository.updateOutcome(42L, TriggerStatus.DISMISSED) }
        coVerify { dismissalTracker.onDismissed(42L) }
        assertTrue(viewModel.uiState.value.isDone)
    }

    @Test
    fun `markCompleted before init completes does not record outcome for invalid id`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger()
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        // Do NOT advance — init coroutine is still in-flight; triggerId is still -1L
        viewModel.init(42L)
        viewModel.markCompleted()
        advanceUntilIdle()
        // With the -1L guard, updateOutcome should never be called with -1L
        coVerify(exactly = 0) { triggerRepository.updateOutcome(-1L, any()) }
    }
}
