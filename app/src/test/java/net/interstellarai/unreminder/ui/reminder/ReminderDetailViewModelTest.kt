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
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.repository.HabitLevelDescriptionRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.domain.model.VariantShape
import net.interstellarai.unreminder.service.notification.MascotSprites
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.notification.SpriteResolver
import net.interstellarai.unreminder.service.trigger.DismissalTracker
import net.interstellarai.unreminder.widget.PullCompletionRecorder
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
    private lateinit var variationRepository: VariationRepository
    private lateinit var levelDescriptionRepository: HabitLevelDescriptionRepository
    private lateinit var dismissalTracker: DismissalTracker
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var widgetRefresher: WidgetRefresher
    private lateinit var completionRecorder: PullCompletionRecorder
    private lateinit var viewModel: ReminderDetailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        triggerRepository = mockk(relaxUnitFun = true)
        habitRepository = mockk(relaxUnitFun = true)
        variationRepository = mockk(relaxUnitFun = true)
        levelDescriptionRepository = mockk(relaxUnitFun = true)
        dismissalTracker = mockk(relaxUnitFun = true)
        notificationHelper = mockk(relaxUnitFun = true)
        widgetRefresher = mockk(relaxUnitFun = true)
        completionRecorder = mockk(relaxUnitFun = true)
        coEvery { triggerRepository.recordOutcome(any(), any()) } returns true
        viewModel = ReminderDetailViewModel(
            triggerRepository,
            habitRepository,
            variationRepository,
            levelDescriptionRepository,
            dismissalTracker,
            notificationHelper,
            widgetRefresher,
            completionRecorder,
            SpriteResolver(),
            testDispatcher,
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
        spriteTag: String? = null,
    ) = TriggerEntity(
        id = 42L,
        habitId = habitId,
        scheduledAt = Instant.EPOCH,
        status = status,
        generatedPrompt = prompt,
        actionUrl = actionUrl,
        spriteTag = spriteTag,
    )

    private fun makeHabit(id: Long = 1L, name: String = "Meditate", level: Int = 3) =
        HabitEntity(id = id, name = name, dedicationLevel = level)

    private fun makeVariation(
        text: String = "breathe slowly",
        actionUrl: String? = null,
        consumedAt: Instant? = null,
        spriteTag: String? = null,
    ) = VariationEntity(
        id = 11L,
        habitId = 1L,
        text = text,
        promptFingerprint = "fp",
        generatedAt = Instant.EPOCH,
        consumedAt = consumedAt,
        actionUrl = actionUrl,
        spriteTag = spriteTag,
        shape = VariantShape.STATEMENT,
    )

    private val taggedSprite = MascotSprites.entries.first { it.tag == "wizard_starry_robe" }.drawableRes

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
        assertNull(viewModel.uiState.value.spriteRes)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `init resolves the sprite the notification was posted with`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger(spriteTag = "wizard_starry_robe")
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        viewModel.init(42L)
        advanceUntilIdle()
        assertEquals(taggedSprite, viewModel.uiState.value.spriteRes)
    }

    @Test
    fun `init on a pre-migration trigger rotates the sprite by trigger id`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger(spriteTag = null)
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        viewModel.init(42L)
        advanceUntilIdle()
        assertEquals(SpriteResolver().resolve(null, 42L), viewModel.uiState.value.spriteRes)
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
        coVerify(exactly = 0) { completionRecorder.complete(any(), any(), any()) }
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
        // Do NOT advance — init coroutine is still in-flight; the target is still unset
        viewModel.init(42L)
        viewModel.markCompleted()
        advanceUntilIdle()
        coVerify(exactly = 0) { triggerRepository.recordOutcome(any(), any()) }
        coVerify(exactly = 0) { completionRecorder.complete(any(), any(), any()) }
    }

    @Test
    fun `initVariant shows the variation's text, the habit and its video, and records nothing`() = runTest {
        val url = "https://www.youtube.com/results?search_query=box+breathing"
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit(name = "Meditation")
        coEvery { variationRepository.getById(11L) } returns makeVariation(text = "four counts in", actionUrl = url)
        viewModel.initVariant(1L, 11L)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("four counts in", state.promptText)
        assertEquals("Meditation", state.habitName)
        assertEquals(3, state.dedicationLevel)
        assertEquals(url, state.videoUrl)
        assertFalse(state.isLoading)
        assertTrue(state.canComplete)
        coVerify(exactly = 0) { triggerRepository.recordOutcome(any(), any()) }
        coVerify(exactly = 0) { triggerRepository.insert(any()) }
        verify(exactly = 0) { notificationHelper.cancelNotification(any()) }
        coVerify(exactly = 0) { dismissalTracker.onCompleted(any()) }
        coVerify(exactly = 0) { dismissalTracker.onDismissed(any()) }
    }

    @Test
    fun `initVariant on a fallback row uses the level description and has no video`() = runTest {
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit(level = 3)
        coEvery { levelDescriptionRepository.getDescriptionForLevel(1L, 3) } returns "sit for two minutes"
        viewModel.initVariant(1L, null)
        advanceUntilIdle()
        assertEquals("sit for two minutes", viewModel.uiState.value.promptText)
        assertNull(viewModel.uiState.value.videoUrl)
        assertTrue(viewModel.uiState.value.canComplete)
        coVerify(exactly = 0) { variationRepository.getById(any()) }
    }

    @Test
    fun `initVariant on a consumed variation still shows its words`() = runTest {
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        coEvery { variationRepository.getById(11L) } returns makeVariation(text = "already claimed", consumedAt = Instant.EPOCH)
        viewModel.initVariant(1L, 11L)
        advanceUntilIdle()
        assertEquals("already claimed", viewModel.uiState.value.promptText)
    }

    @Test
    fun `initVariant whose variation was pruned falls back to the level description`() = runTest {
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit(level = 3)
        coEvery { variationRepository.getById(11L) } returns null
        coEvery { levelDescriptionRepository.getDescriptionForLevel(1L, 3) } returns "sit for two minutes"
        viewModel.initVariant(1L, 11L)
        advanceUntilIdle()
        assertEquals("sit for two minutes", viewModel.uiState.value.promptText)
        assertTrue(viewModel.uiState.value.canComplete)
    }

    @Test
    fun `initVariant resolves the variation's sprite by habit id`() = runTest {
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        coEvery { variationRepository.getById(11L) } returns makeVariation(spriteTag = "wizard_starry_robe")
        viewModel.initVariant(1L, 11L)
        advanceUntilIdle()
        assertEquals(taggedSprite, viewModel.uiState.value.spriteRes)
    }

    @Test
    fun `initVariant on a pruned variation rotates the sprite by habit id`() = runTest {
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit(level = 3)
        coEvery { variationRepository.getById(11L) } returns null
        coEvery { levelDescriptionRepository.getDescriptionForLevel(1L, 3) } returns "sit for two minutes"
        viewModel.initVariant(1L, 11L)
        advanceUntilIdle()
        assertEquals(SpriteResolver().resolve(null, 1L), viewModel.uiState.value.spriteRes)
    }

    @Test
    fun `the layout is keyed on the target`() = runTest {
        coEvery { triggerRepository.getById(42L) } returns makeTrigger()
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit(level = 3)
        coEvery { variationRepository.getById(11L) } returns makeVariation()
        coEvery { levelDescriptionRepository.getDescriptionForLevel(1L, 3) } returns "sit for two minutes"

        viewModel.init(42L)
        advanceUntilIdle()
        assertEquals(ReminderDetailLayout.forTarget(ReminderDetailTarget.Trigger(42L)), viewModel.uiState.value.layout)

        viewModel.initVariant(1L, 11L)
        advanceUntilIdle()
        assertEquals(ReminderDetailLayout.forTarget(ReminderDetailTarget.Variant(1L, 11L)), viewModel.uiState.value.layout)

        viewModel.initVariant(1L, null)
        advanceUntilIdle()
        assertEquals(ReminderDetailLayout.forTarget(ReminderDetailTarget.Variant(1L, null)), viewModel.uiState.value.layout)
    }

    @Test
    fun `initVariant for a deleted habit hides Did it`() = runTest {
        coEvery { habitRepository.getByIdOnce(1L) } returns null
        coEvery { variationRepository.getById(11L) } returns null
        viewModel.initVariant(1L, 11L)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.canComplete)
        assertNull(viewModel.uiState.value.spriteRes)
    }

    @Test
    fun `markCompleted on a variant completes through the pull recorder with the detail source`() = runTest {
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        coEvery { variationRepository.getById(11L) } returns makeVariation()
        viewModel.initVariant(1L, 11L)
        advanceUntilIdle()
        viewModel.markCompleted()
        advanceUntilIdle()
        coVerify(exactly = 1) { completionRecorder.complete(1L, 11L, "detail") }
        verify(exactly = 1) { widgetRefresher.refresh() }
        coVerify(exactly = 0) { triggerRepository.recordOutcome(any(), any()) }
        verify(exactly = 0) { notificationHelper.cancelNotification(any()) }
        assertTrue(viewModel.uiState.value.isDone)
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun `markCompleted on a fallback row passes no variation`() = runTest {
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        coEvery { levelDescriptionRepository.getDescriptionForLevel(1L, 3) } returns "sit for two minutes"
        viewModel.initVariant(1L, null)
        advanceUntilIdle()
        viewModel.markCompleted()
        advanceUntilIdle()
        coVerify(exactly = 1) { completionRecorder.complete(1L, null, "detail") }
        assertTrue(viewModel.uiState.value.isDone)
    }

    @Test
    fun `markCompleted on a variant whose write fails stays on screen`() = runTest {
        coEvery { habitRepository.getByIdOnce(1L) } returns makeHabit()
        coEvery { variationRepository.getById(11L) } returns makeVariation()
        coEvery { completionRecorder.complete(any(), any(), any()) } throws IllegalStateException("disk full")
        viewModel.initVariant(1L, 11L)
        advanceUntilIdle()
        viewModel.markCompleted()
        advanceUntilIdle()
        verify(exactly = 0) { widgetRefresher.refresh() }
        assertFalse(viewModel.uiState.value.isDone)
        assertFalse(viewModel.uiState.value.isProcessing)
        assertTrue(viewModel.uiState.value.canComplete)
    }
}
