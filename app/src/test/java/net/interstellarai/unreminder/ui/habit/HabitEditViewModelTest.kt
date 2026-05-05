package net.interstellarai.unreminder.ui.habit

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.LlmUnavailableException
import net.interstellarai.unreminder.service.llm.PromptGenerator
import net.interstellarai.unreminder.service.worker.RefillScheduler
import net.interstellarai.unreminder.service.worker.SpendCapExceededException
import net.interstellarai.unreminder.service.worker.WorkerAuthException
import net.interstellarai.unreminder.service.worker.WorkerError
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.io.IOException
import java.net.UnknownHostException
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
    private val mockGeofenceManager: GeofenceManager = mockk(relaxed = true)
    private val mockTriggerRepository: TriggerRepository = mockk(relaxed = true)
    private lateinit var viewModel: HabitEditViewModel

    // Backing flow for geofenceManager.currentLocationIds — tests mutate this directly.
    private val currentLocationIdsFlow = MutableStateFlow<Set<Long>>(emptySet())

    private val testLadder = listOf("3 deep breaths", "", "", "20-minute guided meditation", "", "")

    private val testHabit = HabitEntity(
        id = 1L,
        name = "meditation",
        descriptionLadder = testLadder,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        currentLocationIdsFlow.value = emptySet()
        every { mockLocationRepository.getAll() } returns flowOf(emptyList())
        every { mockWindowRepository.getAll() } returns flowOf(emptyList())
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow<AiStatus>(AiStatus.Ready)
        every { mockGeofenceManager.currentLocationIds } returns currentLocationIdsFlow.asStateFlow()
        coEvery { mockTriggerRepository.countCompletedSince(any(), any()) } returns 0
        coEvery { mockTriggerRepository.countNonScheduledSince(any(), any()) } returns 0
        coEvery { mockTriggerRepository.getLastFiredOrDismissedForHabit(any()) } returns null
        viewModel = HabitEditViewModel(
            mockHabitRepository,
            mockLocationRepository,
            mockWindowRepository,
            mockPromptGenerator,
            mockRefillScheduler,
            mockVariationRepository,
            mockGeofenceManager,
            mockTriggerRepository,
        )
        viewModel.updateName("meditation")
        testLadder.forEachIndexed { i, text -> viewModel.updateDescriptionAtLevel(i, text) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- autofillWithAi ---

    @Test
    fun `autofillWithAi updates fields and clears isGeneratingFields on success`() = runTest(testDispatcher) {
        val ladder = listOf("3 deep breaths", "", "", "20-min guided session", "", "")
        coEvery { mockPromptGenerator.generateHabitFields("meditation") } returns
            AiHabitFields(ladder)

        viewModel.autofillWithAi()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGeneratingFields)
        assertEquals(ladder, state.descriptionLadder)
        assertNull(state.errorMessage)
    }

    @Test
    fun `autofillWithAi sets errorMessage and resets isGeneratingFields on failure`() = runTest(testDispatcher) {
        coEvery { mockPromptGenerator.generateHabitFields(any()) } throws
            LlmUnavailableException()

        viewModel.autofillWithAi()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGeneratingFields)
        assertFalse(state.fieldsFlashing)
        assertEquals("AI unavailable — fill in manually.", state.errorMessage)
    }

    @Test
    fun `autofillWithAi captures unexpected exception to Sentry`() = runTest(testDispatcher) {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID

        coEvery { mockPromptGenerator.generateHabitFields(any()) } throws
            RuntimeException("unexpected network failure")

        viewModel.autofillWithAi()
        advanceUntilIdle()

        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
        unmockkStatic(Sentry::class)
    }

    @Test
    fun `autofillWithAi does not send Sentry event for LLM unavailable`() = runTest(testDispatcher) {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID

        coEvery { mockPromptGenerator.generateHabitFields(any()) } throws
            LlmUnavailableException()

        viewModel.autofillWithAi()
        advanceUntilIdle()

        verify(exactly = 0) { Sentry.captureException(any(), any<ScopeCallback>()) }
        unmockkStatic(Sentry::class)
    }

    // --- previewNotification (peeks variation pool; falls back to errorMessage) ---

    @Test
    fun `previewNotification shows dialog with pooled variation when available`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(testHabit)
        coEvery { mockVariationRepository.peekUnused(testHabit.id) } returns
            "Open your journal and write one line"
        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()

        viewModel.previewNotification()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showPreviewDialog)
        assertEquals("Open your journal and write one line", state.previewNotification)
        assertNull(state.errorMessage)
    }

    @Test
    fun `previewNotification surfaces errorMessage and does not open dialog when pool is empty`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(testHabit)
        coEvery { mockVariationRepository.peekUnused(testHabit.id) } returns null
        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()

        viewModel.previewNotification()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showPreviewDialog)
        assertNull(state.previewNotification)
        assertEquals(
            "Notifications are still being generated — try again in a moment.",
            state.errorMessage
        )
    }

    @Test
    fun `previewNotification surfaces errorMessage when habit is unsaved`() = runTest(testDispatcher) {
        // setup() did not call loadHabit, so _habitId is null
        viewModel.previewNotification()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showPreviewDialog)
        assertNull(state.previewNotification)
        assertEquals(
            "Save the habit first to preview a real notification.",
            state.errorMessage
        )
        coVerify(exactly = 0) { mockVariationRepository.peekUnused(any()) }
    }

    @Test
    fun `previewNotification sets errorMessage when peekUnused throws`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(testHabit)
        coEvery { mockVariationRepository.peekUnused(testHabit.id) } throws RuntimeException("db error")
        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()

        viewModel.previewNotification()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showPreviewDialog)
        assertEquals("Preview unavailable — try again.", state.errorMessage)
    }

    // --- dismissPreviewDialog ---

    @Test
    fun `dismissPreviewDialog clears showPreviewDialog and previewNotification`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(testHabit)
        coEvery { mockVariationRepository.peekUnused(testHabit.id) } returns "preview text"
        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()
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
            AiHabitFields(listOf("desc", "", "", "", "", ""))

        viewModel.autofillWithAi()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.fieldsFlashing)
    }

    @Test
    fun `clearFieldsFlash resets fieldsFlashing to false`() = runTest(testDispatcher) {
        coEvery { mockPromptGenerator.generateHabitFields("meditation") } returns
            AiHabitFields(listOf("desc", "", "", "", "", ""))

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

        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()
        // viewModel state now matches testHabit exactly (name, descriptionLadder)

        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockVariationRepository.deleteForHabit(any()) }
        coVerify(exactly = 0) { mockRefillScheduler.enqueueForHabit(any()) }
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `save deletes pool and enqueues refill when only description ladder changed`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(testHabit)
        coEvery { mockHabitRepository.update(any()) } returns Unit

        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()

        // Change only description ladder, not the name
        viewModel.updateDescriptionAtLevel(0, "NEW level 0")

        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockVariationRepository.deleteForHabit(testHabit.id) }
        coVerify(exactly = 1) { mockRefillScheduler.enqueueForHabit(testHabit.id) }
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `loadHabit sets errorMessage when repository throws`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getById(99L) } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("db error")
        }

        viewModel.loadHabit(99L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Failed to load habit.", state.errorMessage)
        assertFalse(state.isLoading)
    }

    // --- aiStatus ---

    @Test
    fun `aiStatus delegates directly to promptGenerator aiStatus`() = runTest(testDispatcher) {
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow(AiStatus.Unavailable)
        val vm = HabitEditViewModel(
            mockHabitRepository, mockLocationRepository, mockWindowRepository,
            mockPromptGenerator, mockRefillScheduler, mockVariationRepository,
            mockGeofenceManager, mockTriggerRepository,
        )
        assertEquals(AiStatus.Unavailable, vm.aiStatus.value)
    }

    @Test
    fun `aiStatus is Ready when promptGenerator reports Ready`() = runTest(testDispatcher) {
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow(AiStatus.Ready)
        val vm = HabitEditViewModel(
            mockHabitRepository, mockLocationRepository, mockWindowRepository,
            mockPromptGenerator, mockRefillScheduler, mockVariationRepository,
            mockGeofenceManager, mockTriggerRepository,
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

    @Test
    fun `updateDescriptionAtLevel updates correct slot`() = runTest(testDispatcher) {
        viewModel.updateDescriptionAtLevel(2, "half session")
        assertEquals("half session", viewModel.uiState.value.descriptionLadder[2])
    }

    @Test
    fun `updateDescriptionAtLevel out-of-range index is ignored`() = runTest(testDispatcher) {
        val before = viewModel.uiState.value.descriptionLadder.toList()
        viewModel.updateDescriptionAtLevel(6, "too high")
        assertEquals(before, viewModel.uiState.value.descriptionLadder)
    }

    @Test
    fun `updateDescriptionAtLevel negative index is ignored`() = runTest(testDispatcher) {
        val before = viewModel.uiState.value.descriptionLadder.toList()
        viewModel.updateDescriptionAtLevel(-1, "negative")
        assertEquals(before, viewModel.uiState.value.descriptionLadder)
    }

    @Test
    fun `updateDedicationLevel updates dedicationLevel in uiState`() = runTest(testDispatcher) {
        viewModel.updateDedicationLevel(4)
        assertEquals(4, viewModel.uiState.value.dedicationLevel)
    }

    @Test
    fun `updateAutoAdjustLevel updates autoAdjustLevel in uiState`() = runTest(testDispatcher) {
        viewModel.updateAutoAdjustLevel(false)
        assertFalse(viewModel.uiState.value.autoAdjustLevel)
    }

    @Test
    fun `autofillWithAi shows retry message and does not capture to Sentry on 5xx WorkerError`() = runTest(testDispatcher) {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID

        coEvery { mockPromptGenerator.generateHabitFields(any()) } throws WorkerError(502, """{"error":"Upstream unavailable or returned invalid response"}""")

        viewModel.autofillWithAi()
        advanceUntilIdle()

        assertEquals("Service temporarily unavailable — please try again.", viewModel.uiState.value.errorMessage)
        verify(exactly = 0) { Sentry.captureException(any(), any<ScopeCallback>()) }
        unmockkStatic(Sentry::class)
    }

    @Test
    fun `autofillWithAi shows connection message and does not capture to Sentry on UnknownHostException`() = runTest(testDispatcher) {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID

        coEvery { mockPromptGenerator.generateHabitFields(any()) } throws
            UnknownHostException("un-reminder-worker.alexsiri7.workers.dev")

        viewModel.autofillWithAi()
        advanceUntilIdle()

        assertEquals("AI unavailable — check your connection.", viewModel.uiState.value.errorMessage)
        verify(exactly = 0) { Sentry.captureException(any(), any<ScopeCallback>()) }
        unmockkStatic(Sentry::class)
    }

    @Test
    fun `autofillWithAi shows connection message and does not capture to Sentry on generic IOException`() = runTest(testDispatcher) {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID

        coEvery { mockPromptGenerator.generateHabitFields(any()) } throws
            IOException("connection reset")

        viewModel.autofillWithAi()
        advanceUntilIdle()

        assertEquals("AI unavailable — check your connection.", viewModel.uiState.value.errorMessage)
        verify(exactly = 0) { Sentry.captureException(any(), any<ScopeCallback>()) }
        unmockkStatic(Sentry::class)
    }

    @Test
    fun `autofillWithAi captures to Sentry and shows generic message on non-5xx WorkerError`() = runTest(testDispatcher) {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID

        coEvery { mockPromptGenerator.generateHabitFields(any()) } throws WorkerError(422, """{"error":"Invalid input"}""")

        viewModel.autofillWithAi()
        advanceUntilIdle()

        assertEquals("AI unavailable — fill in manually.", viewModel.uiState.value.errorMessage)
        verify(exactly = 1) { Sentry.captureException(any(), any<ScopeCallback>()) }
        unmockkStatic(Sentry::class)
    }

    // --- computeAvailability (via loadHabit) ---

    @Test
    fun `availability is Available when no constraints apply`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(testHabit)
        coEvery { mockHabitRepository.getLocationIds(testHabit.id) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(testHabit.id) } returns emptyList()

        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()

        assertEquals(AvailabilityStatus.Available, viewModel.uiState.value.availabilityStatus)
    }

    @Test
    fun `availability includes INACTIVE when habit is inactive`() = runTest(testDispatcher) {
        val inactive = testHabit.copy(active = false)
        coEvery { mockHabitRepository.getById(inactive.id) } returns flowOf(inactive)
        coEvery { mockHabitRepository.getLocationIds(inactive.id) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(inactive.id) } returns emptyList()

        viewModel.loadHabit(inactive.id)
        advanceUntilIdle()

        val status = viewModel.uiState.value.availabilityStatus as AvailabilityStatus.Unavailable
        assertTrue(UnavailableReason.INACTIVE in status.reasons)
    }

    @Test
    fun `availability includes LOCATION when current geofence does not overlap habit locations`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(testHabit)
        coEvery { mockHabitRepository.getLocationIds(testHabit.id) } returns listOf(1L, 2L)
        coEvery { mockHabitRepository.getWindowIds(testHabit.id) } returns emptyList()
        currentLocationIdsFlow.value = setOf(99L)

        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()

        val status = viewModel.uiState.value.availabilityStatus as AvailabilityStatus.Unavailable
        assertTrue(UnavailableReason.LOCATION in status.reasons)
    }

    @Test
    fun `availability omits LOCATION when current geofence overlaps habit locations`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(testHabit)
        coEvery { mockHabitRepository.getLocationIds(testHabit.id) } returns listOf(1L, 2L)
        coEvery { mockHabitRepository.getWindowIds(testHabit.id) } returns emptyList()
        currentLocationIdsFlow.value = setOf(2L, 99L)

        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()

        assertEquals(AvailabilityStatus.Available, viewModel.uiState.value.availabilityStatus)
    }

    @Test
    fun `availability updates reactively when geofence location changes after habit is loaded`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(testHabit)
        coEvery { mockHabitRepository.getLocationIds(testHabit.id) } returns listOf(1L, 2L)
        coEvery { mockHabitRepository.getWindowIds(testHabit.id) } returns emptyList()
        // Start with wrong location — badge shows LOCATION
        currentLocationIdsFlow.value = setOf(99L)

        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()

        val before = viewModel.uiState.value.availabilityStatus as AvailabilityStatus.Unavailable
        assertTrue(UnavailableReason.LOCATION in before.reasons)

        // Simulate INITIAL_TRIGGER_ENTER firing — now at a matching location
        currentLocationIdsFlow.value = setOf(2L)
        advanceUntilIdle()

        assertEquals(AvailabilityStatus.Available, viewModel.uiState.value.availabilityStatus)
    }

    @Test
    fun `availability includes COMPLETED when at least one COMPLETED trigger today`() = runTest(testDispatcher) {
        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(testHabit)
        coEvery { mockHabitRepository.getLocationIds(testHabit.id) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(testHabit.id) } returns emptyList()
        coEvery { mockTriggerRepository.countCompletedSince(testHabit.id, any()) } returns 1

        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()

        val status = viewModel.uiState.value.availabilityStatus as AvailabilityStatus.Unavailable
        assertTrue(UnavailableReason.COMPLETED in status.reasons)
    }

    @Test
    fun `availability omits COOLDOWN when cooldownMinutes is 0 even after recent DISMISSED`() = runTest(testDispatcher) {
        val noCooldown = testHabit.copy(cooldownMinutes = 0)
        coEvery { mockHabitRepository.getById(noCooldown.id) } returns flowOf(noCooldown)
        coEvery { mockHabitRepository.getLocationIds(noCooldown.id) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(noCooldown.id) } returns emptyList()
        coEvery { mockTriggerRepository.getLastFiredOrDismissedForHabit(any()) } returns Instant.now().toEpochMilli()

        viewModel.loadHabit(noCooldown.id)
        advanceUntilIdle()

        assertEquals(AvailabilityStatus.Available, viewModel.uiState.value.availabilityStatus)
    }

    @Test
    fun `availability includes COOLDOWN when last DISMISSED is within cooldown window`() = runTest(testDispatcher) {
        val cooldownHabit = testHabit.copy(cooldownMinutes = 60)
        val tenMinAgo = Instant.now().toEpochMilli() - 10 * 60 * 1000L
        coEvery { mockHabitRepository.getById(cooldownHabit.id) } returns flowOf(cooldownHabit)
        coEvery { mockHabitRepository.getLocationIds(cooldownHabit.id) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(cooldownHabit.id) } returns emptyList()
        coEvery { mockTriggerRepository.getLastFiredOrDismissedForHabit(cooldownHabit.id) } returns tenMinAgo

        viewModel.loadHabit(cooldownHabit.id)
        advanceUntilIdle()

        val status = viewModel.uiState.value.availabilityStatus as AvailabilityStatus.Unavailable
        assertTrue(UnavailableReason.COOLDOWN in status.reasons)
    }

    @Test
    fun `availability omits COOLDOWN when last DISMISSED is outside cooldown window`() = runTest(testDispatcher) {
        val cooldownHabit = testHabit.copy(cooldownMinutes = 60)
        val twoHoursAgo = Instant.now().toEpochMilli() - 2 * 60 * 60 * 1000L
        coEvery { mockHabitRepository.getById(cooldownHabit.id) } returns flowOf(cooldownHabit)
        coEvery { mockHabitRepository.getLocationIds(cooldownHabit.id) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(cooldownHabit.id) } returns emptyList()
        coEvery { mockTriggerRepository.getLastFiredOrDismissedForHabit(cooldownHabit.id) } returns twoHoursAgo

        viewModel.loadHabit(cooldownHabit.id)
        advanceUntilIdle()

        assertEquals(AvailabilityStatus.Available, viewModel.uiState.value.availabilityStatus)
    }

    @Test
    fun `availability includes DAILY_LIMIT when non-scheduled count meets the limit`() = runTest(testDispatcher) {
        val limit2 = testHabit.copy(dailyLimit = 2, cooldownMinutes = 0)
        coEvery { mockHabitRepository.getById(limit2.id) } returns flowOf(limit2)
        coEvery { mockHabitRepository.getLocationIds(limit2.id) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(limit2.id) } returns emptyList()
        coEvery { mockTriggerRepository.countNonScheduledSince(limit2.id, any()) } returns 2

        viewModel.loadHabit(limit2.id)
        advanceUntilIdle()

        val status = viewModel.uiState.value.availabilityStatus as AvailabilityStatus.Unavailable
        assertTrue(
            "daily limit hit but UI is not surfacing DAILY_LIMIT — SQL/Kotlin parity broken",
            UnavailableReason.DAILY_LIMIT in status.reasons
        )
    }

    @Test
    fun `availability omits DAILY_LIMIT when non-scheduled count is under the limit`() = runTest(testDispatcher) {
        val limit3 = testHabit.copy(dailyLimit = 3, cooldownMinutes = 0)
        coEvery { mockHabitRepository.getById(limit3.id) } returns flowOf(limit3)
        coEvery { mockHabitRepository.getLocationIds(limit3.id) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(limit3.id) } returns emptyList()
        coEvery { mockTriggerRepository.countNonScheduledSince(limit3.id, any()) } returns 1

        viewModel.loadHabit(limit3.id)
        advanceUntilIdle()

        assertEquals(AvailabilityStatus.Available, viewModel.uiState.value.availabilityStatus)
    }

    @Test
    fun `availability falls back to NewHabit (badge hidden) when computeAvailability throws`() = runTest(testDispatcher) {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0

        coEvery { mockHabitRepository.getById(testHabit.id) } returns flowOf(testHabit)
        coEvery { mockHabitRepository.getLocationIds(testHabit.id) } returns emptyList()
        coEvery { mockHabitRepository.getWindowIds(testHabit.id) } returns emptyList()
        coEvery { mockTriggerRepository.countCompletedSince(any(), any()) } throws RuntimeException("room boom")

        viewModel.loadHabit(testHabit.id)
        advanceUntilIdle()

        // Edit screen still loaded — only the badge is hidden.
        val state = viewModel.uiState.value
        assertEquals(AvailabilityStatus.NewHabit, state.availabilityStatus)
        assertNull(state.errorMessage)
        assertEquals(testHabit.name, state.name)
        unmockkStatic(android.util.Log::class)
    }
}
