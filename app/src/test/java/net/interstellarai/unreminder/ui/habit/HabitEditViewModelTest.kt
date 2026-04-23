package net.interstellarai.unreminder.ui.habit

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.HabitLevelDescriptionEntity
import net.interstellarai.unreminder.data.repository.HabitLevelDescriptionRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.PromptGenerator
import net.interstellarai.unreminder.service.worker.RefillScheduler
import net.interstellarai.unreminder.service.worker.SpendCapExceededException
import net.interstellarai.unreminder.service.worker.WorkerAuthException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import io.sentry.ScopeCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HabitEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockPromptGenerator: PromptGenerator = mockk()
    private val mockHabitRepository: HabitRepository = mockk(relaxed = true)
    private val mockLocationRepository: LocationRepository = mockk(relaxed = true)
    private val mockRefillScheduler: RefillScheduler = mockk(relaxed = true)
    private val mockVariationRepository: VariationRepository = mockk(relaxUnitFun = true)
    private val mockWindowRepository: WindowRepository = mockk(relaxed = true)
    private val mockLevelDescriptionRepository: HabitLevelDescriptionRepository = mockk(relaxed = true)
    private lateinit var viewModel: HabitEditViewModel

    private val testHabit = HabitEntity(
        id = 1L,
        name = "meditation",
        dedicationLevel = 0,
        autoAdjustLevel = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { mockLocationRepository.getAll() } returns flowOf(emptyList())
        every { mockWindowRepository.getAll() } returns flowOf(emptyList())
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow<AiStatus>(AiStatus.Ready)
        coEvery { mockLevelDescriptionRepository.getDescriptionsForHabit(any()) } returns emptyList()
        viewModel = HabitEditViewModel(
            mockHabitRepository,
            mockLocationRepository,
            mockWindowRepository,
            mockPromptGenerator,
            mockRefillScheduler,
            mockVariationRepository,
            mockLevelDescriptionRepository,
        )
        viewModel.updateName("meditation")
        viewModel.updateLevelDescription(0, "3 deep breaths")
        viewModel.updateLevelDescription(5, "20-minute guided meditation")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- autofillWithAi ---

    @Test
    fun `autofillWithAi updates fields and clears isGeneratingFields on success`() = runTest(testDispatcher) {
        coEvery { mockPromptGenerator.generateHabitFields("meditation") } returns
            AiHabitFields(listOf("3 deep breaths", "", "", "", "", "20-min guided session"))

        viewModel.autofillWithAi()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGeneratingFields)
        assertEquals("3 deep breaths", state.levelDescriptions[0])
        assertEquals("20-min guided session", state.levelDescriptions[5])
        assertNull(state.errorMessage)
    }

    @Test
    fun `autofillWithAi sets errorMessage and resets isGeneratingFields on failure`() = runTest(testDispatcher) {
        coEvery { mockPromptGenerator.generateHabitFields(any()) } throws
            IllegalStateException("LLM unavailable")

        viewModel.autofillWithAi()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGeneratingFields)
        assertFalse(state.fieldsFlashing)
        assertEquals("AI unavailable — fill in manually.", state.errorMessage)
    }

    @Test
    fun `autofillWithAi captures exception to Sentry on failure`() = runTest(testDispatcher) {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID

        coEvery { mockPromptGenerator.generateHabitFields(any()) } throws
            IllegalStateException("LLM unavailable")

        viewModel.autofillWithAi()
        advanceUntilIdle()

        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
        unmockkStatic(Sentry::class)
    }

    // --- previewNotification ---

    @Test
    fun `previewNotification shows dialog with text and clears flag on success`() = runTest(testDispatcher) {
        coEvery {
            mockPromptGenerator.previewHabitNotification(any(), any(), any())
        } returns "Time to meditate — even 3 breaths counts"

        viewModel.previewNotification()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showPreviewDialog)
        assertEquals("Time to meditate — even 3 breaths counts", state.previewNotification)
        assertFalse(state.isGeneratingFields)
        assertNull(state.errorMessage)
    }

    @Test
    fun `previewNotification sets errorMessage and resets flag on failure`() = runTest(testDispatcher) {
        coEvery {
            mockPromptGenerator.previewHabitNotification(any(), any(), any())
        } throws IllegalStateException("LLM unavailable")

        viewModel.previewNotification()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showPreviewDialog)
        assertFalse(state.isGeneratingFields)
        assertEquals("AI unavailable — preview not available.", state.errorMessage)
    }

    // --- dismissPreviewDialog ---

    @Test
    fun `dismissPreviewDialog clears showPreviewDialog and previewNotification`() = runTest(testDispatcher) {
        coEvery { mockPromptGenerator.previewHabitNotification(any(), any(), any()) } returns "preview"
        viewModel.previewNotification()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showPreviewDialog)

        viewModel.dismissPreviewDialog()

        val state = viewModel.uiState.value
        assertFalse(state.showPreviewDialog)
        assertNull(state.previewNotification)
    }

    // --- clearError ---

    @Test
    fun `clearError nullifies errorMessage`() = runTest(testDispatcher) {
        coEvery { mockPromptGenerator.generateHabitFields(any()) } throws RuntimeException("boom")
        viewModel.autofillWithAi()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    // --- fieldsFlashing ---

    @Test
    fun `autofillWithAi success sets fieldsFlashing to true`() = runTest(testDispatcher) {
        coEvery { mockPromptGenerator.generateHabitFields("meditation") } returns
            AiHabitFields(listOf("low", "", "", "", "", "full"))

        viewModel.autofillWithAi()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.fieldsFlashing)
    }

    @Test
    fun `clearFieldsFlash resets fieldsFlashing to false`() = runTest(testDispatcher) {
        coEvery { mockPromptGenerator.generateHabitFields("meditation") } returns
            AiHabitFields(listOf("low", "", "", "", "", "full"))

        viewModel.autofillWithAi()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.fieldsFlashing)

        viewModel.clearFieldsFlash()

        assertFalse(viewModel.uiState.value.fieldsFlashing)
    }

    // --- SpendCap ---

    @Test
    fun `autofillWithAi sets showSpendCapLink on SpendCapExceededException`() =
        runTest(testDispatcher) {
            coEvery {
                mockPromptGenerator.generateHabitFields(any())
            } throws SpendCapExceededException()

            viewModel.autofillWithAi()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.showSpendCapLink)
            assertNull(state.errorMessage)
            assertFalse(state.isGeneratingFields)
        }

    @Test
    fun `previewNotification sets showSpendCapLink on SpendCapExceededException`() =
        runTest(testDispatcher) {
            coEvery {
                mockPromptGenerator.previewHabitNotification(any(), any(), any())
            } throws SpendCapExceededException()

            viewModel.previewNotification()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.showSpendCapLink)
            assertNull(state.errorMessage)
            assertFalse(state.isGeneratingFields)
        }

    // --- WorkerAuthException ---

    @Test
    fun `autofillWithAi sets errorMessage on WorkerAuthException`() =
        runTest(testDispatcher) {
            coEvery {
                mockPromptGenerator.generateHabitFields(any())
            } throws WorkerAuthException()

            viewModel.autofillWithAi()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Wrong worker secret \u2014 check Settings.", state.errorMessage)
            assertFalse(state.isGeneratingFields)
            assertFalse(state.showSpendCapLink)
        }

    @Test
    fun `previewNotification sets errorMessage on WorkerAuthException`() =
        runTest(testDispatcher) {
            coEvery {
                mockPromptGenerator.previewHabitNotification(any(), any(), any())
            } throws WorkerAuthException()

            viewModel.previewNotification()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Wrong worker secret \u2014 check Settings.", state.errorMessage)
            assertFalse(state.isGeneratingFields)
        }

    // --- save: refill scheduling ---

    @Test
    fun `save enqueues refill for new habit without deleting pool`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.insert(any()) } returns 42L

        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRefillScheduler.enqueueForHabit(42L) }
        coVerify(exactly = 0) { mockVariationRepository.deleteForHabit(any()) }
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `save deletes pool and enqueues refill when prompt fields changed`() = runTest(testDispatcher) {
        val existingWithDifferentName = testHabit.copy(name = "OLD NAME")
        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(existingWithDifferentName)
        coEvery { mockHabitRepository.update(any()) } returns Unit
        coEvery { mockLevelDescriptionRepository.getDescriptionsForHabit(testHabit.id) } returns
            listOf(
                HabitLevelDescriptionEntity(testHabit.id, 0, "3 deep breaths"),
                HabitLevelDescriptionEntity(testHabit.id, 5, "20-minute guided meditation")
            )

        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()
        // The viewModel now has existingHabit = testHabit with name "OLD NAME",
        // but uiState has name "OLD NAME". Update name to trigger prompt change.
        viewModel.updateName("meditation") // differs from "OLD NAME"

        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockVariationRepository.deleteForHabit(testHabit.id) }
        coVerify(exactly = 1) { mockRefillScheduler.enqueueForHabit(testHabit.id) }
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `save does not delete pool or enqueue when no prompt fields changed`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(testHabit)
        coEvery { mockHabitRepository.update(any()) } returns Unit
        coEvery { mockLevelDescriptionRepository.getDescriptionsForHabit(testHabit.id) } returns
            listOf(
                HabitLevelDescriptionEntity(testHabit.id, 0, "3 deep breaths"),
                HabitLevelDescriptionEntity(testHabit.id, 5, "20-minute guided meditation")
            )

        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()
        // viewModel state now matches testHabit exactly (name, levelDescriptions)

        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockVariationRepository.deleteForHabit(any()) }
        coVerify(exactly = 0) { mockRefillScheduler.enqueueForHabit(any()) }
        assertTrue(viewModel.uiState.value.isSaved)
    }

    // --- aiStatus ---

    @Test
    fun `aiStatus delegates directly to promptGenerator aiStatus`() = runTest(testDispatcher) {
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow(AiStatus.Unavailable)
        val vm = HabitEditViewModel(
            mockHabitRepository, mockLocationRepository, mockWindowRepository,
            mockPromptGenerator, mockRefillScheduler, mockVariationRepository,
            mockLevelDescriptionRepository,
        )
        assertEquals(AiStatus.Unavailable, vm.aiStatus.value)
    }

    @Test
    fun `aiStatus is Ready when promptGenerator reports Ready`() = runTest(testDispatcher) {
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow(AiStatus.Ready)
        val vm = HabitEditViewModel(
            mockHabitRepository, mockLocationRepository, mockWindowRepository,
            mockPromptGenerator, mockRefillScheduler, mockVariationRepository,
            mockLevelDescriptionRepository,
        )
        assertEquals(AiStatus.Ready, vm.aiStatus.value)
    }

    // --- toggleWindow ---

    @Test
    fun `toggleWindow adds window id to selectedWindowIds`() = runTest(testDispatcher) {
        viewModel.toggleWindow(10L)
        assertTrue(10L in viewModel.uiState.value.selectedWindowIds)
    }

    @Test
    fun `toggleWindow removes window id when already selected`() = runTest(testDispatcher) {
        viewModel.toggleWindow(10L)
        assertTrue(10L in viewModel.uiState.value.selectedWindowIds)

        viewModel.toggleWindow(10L)
        assertFalse(10L in viewModel.uiState.value.selectedWindowIds)
    }

    @Test
    fun `setAnyTime clears selectedWindowIds`() = runTest(testDispatcher) {
        viewModel.toggleWindow(10L)
        viewModel.toggleWindow(20L)
        assertEquals(setOf(10L, 20L), viewModel.uiState.value.selectedWindowIds)

        viewModel.setAnyTime()
        assertTrue(viewModel.uiState.value.selectedWindowIds.isEmpty())
    }

    @Test
    fun `save calls setWindows with selected window ids`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.insert(any()) } returns 42L

        viewModel.toggleWindow(5L)
        viewModel.toggleWindow(7L)
        viewModel.save()
        advanceUntilIdle()

        coVerify { mockHabitRepository.setWindows(42L, setOf(5L, 7L)) }
        assertTrue(viewModel.uiState.value.isSaved)
    }
}
