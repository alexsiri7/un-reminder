package net.interstellarai.unreminder.ui.habit

import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.FeatureFlagsRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.data.repository.WorkerSettingsRepository
import net.interstellarai.unreminder.domain.model.AiHabitFields
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.PromptGenerator
import net.interstellarai.unreminder.service.worker.RefillScheduler
import net.interstellarai.unreminder.service.worker.RequestyProxyClient
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
import kotlinx.coroutines.launch
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
    private val mockRequestyProxyClient: RequestyProxyClient = mockk()
    private val mockWorkerSettingsRepository: WorkerSettingsRepository = mockk()
    private val mockRefillScheduler: RefillScheduler = mockk(relaxed = true)
    private val mockVariationRepository: VariationRepository = mockk(relaxUnitFun = true)
    private val mockFeatureFlagsRepository: FeatureFlagsRepository = mockk()
    private lateinit var viewModel: HabitEditViewModel

    private val testHabit = HabitEntity(
        id = 1L,
        name = "meditation",
        fullDescription = "20-minute guided meditation",
        lowFloorDescription = "3 deep breaths",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { mockLocationRepository.getAll() } returns flowOf(emptyList())
        every { mockPromptGenerator.downloadProgress } returns MutableStateFlow<Float?>(null)
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow<AiStatus>(AiStatus.Ready)
        // Default: no worker configured — routes all AI calls to on-device promptGenerator
        every { mockWorkerSettingsRepository.effectiveWorkerUrl } returns flowOf("")
        every { mockWorkerSettingsRepository.effectiveWorkerSecret } returns flowOf("")
        every { mockFeatureFlagsRepository.useCloudPool } returns flowOf(false)
        viewModel = HabitEditViewModel(
            mockHabitRepository,
            mockLocationRepository,
            mockPromptGenerator,
            mockRequestyProxyClient,
            mockWorkerSettingsRepository,
            mockRefillScheduler,
            mockVariationRepository,
            mockFeatureFlagsRepository,
        )
        viewModel.updateName("meditation")
        viewModel.updateFullDescription("20-minute guided meditation")
        viewModel.updateLowFloorDescription("3 deep breaths")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- autofillWithAi ---

    @Test
    fun `autofillWithAi updates fields and clears isGeneratingFields on success`() = runTest(testDispatcher) {
        coEvery { mockPromptGenerator.generateHabitFields("meditation") } returns
            AiHabitFields("20-min guided session", "3 deep breaths")

        viewModel.autofillWithAi()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGeneratingFields)
        assertEquals("20-min guided session", state.fullDescription)
        assertEquals("3 deep breaths", state.lowFloorDescription)
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
            mockPromptGenerator.previewHabitNotification(any(), any())
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
            mockPromptGenerator.previewHabitNotification(any(), any())
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
        coEvery { mockPromptGenerator.previewHabitNotification(any(), any()) } returns "preview"
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
            AiHabitFields("desc", "low")

        viewModel.autofillWithAi()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.fieldsFlashing)
    }

    @Test
    fun `clearFieldsFlash resets fieldsFlashing to false`() = runTest(testDispatcher) {
        coEvery { mockPromptGenerator.generateHabitFields("meditation") } returns
            AiHabitFields("desc", "low")

        viewModel.autofillWithAi()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.fieldsFlashing)

        viewModel.clearFieldsFlash()

        assertFalse(viewModel.uiState.value.fieldsFlashing)
    }

    // --- SpendCap via proxy ---

    @Test
    fun `autofillWithAi sets showSpendCapLink and no errorMessage on SpendCapExceededException`() =
        runTest(testDispatcher) {
            // Route through proxy by providing a non-blank URL + secret
            every { mockWorkerSettingsRepository.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
            every { mockWorkerSettingsRepository.effectiveWorkerSecret } returns flowOf("secret")
            coEvery {
                mockRequestyProxyClient.habitFields(any(), any(), any())
            } throws SpendCapExceededException()

            viewModel.autofillWithAi()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.showSpendCapLink)
            assertNull(state.errorMessage)
            assertFalse(state.isGeneratingFields)
        }

    @Test
    fun `autofillWithAi via proxy updates fields on success`() = runTest(testDispatcher) {
        every { mockWorkerSettingsRepository.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
        every { mockWorkerSettingsRepository.effectiveWorkerSecret } returns flowOf("secret")
        coEvery {
            mockRequestyProxyClient.habitFields(any(), any(), any())
        } returns AiHabitFields("Cloud full desc", "Cloud low floor")

        viewModel.autofillWithAi()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGeneratingFields)
        assertEquals("Cloud full desc", state.fullDescription)
        assertEquals("Cloud low floor", state.lowFloorDescription)
        assertTrue(state.fieldsFlashing)
        assertNull(state.errorMessage)
    }

    @Test
    fun `previewNotification via proxy shows dialog on success`() = runTest(testDispatcher) {
        every { mockWorkerSettingsRepository.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
        every { mockWorkerSettingsRepository.effectiveWorkerSecret } returns flowOf("secret")
        coEvery {
            mockRequestyProxyClient.preview(any(), any(), any(), any())
        } returns "Cloud preview text"

        viewModel.previewNotification()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showPreviewDialog)
        assertEquals("Cloud preview text", state.previewNotification)
        assertFalse(state.isGeneratingFields)
        assertNull(state.errorMessage)
    }

    @Test
    fun `previewNotification sets showSpendCapLink on SpendCapExceededException`() =
        runTest(testDispatcher) {
            every { mockWorkerSettingsRepository.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
            every { mockWorkerSettingsRepository.effectiveWorkerSecret } returns flowOf("secret")
            coEvery {
                mockRequestyProxyClient.preview(any(), any(), any(), any())
            } throws SpendCapExceededException()

            viewModel.previewNotification()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.showSpendCapLink)
            assertNull(state.errorMessage)
            assertFalse(state.isGeneratingFields)
        }

    // --- WorkerAuthException via proxy ---

    @Test
    fun `autofillWithAi sets errorMessage on WorkerAuthException`() =
        runTest(testDispatcher) {
            every { mockWorkerSettingsRepository.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
            every { mockWorkerSettingsRepository.effectiveWorkerSecret } returns flowOf("secret")
            coEvery {
                mockRequestyProxyClient.habitFields(any(), any(), any())
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
            every { mockWorkerSettingsRepository.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
            every { mockWorkerSettingsRepository.effectiveWorkerSecret } returns flowOf("secret")
            coEvery {
                mockRequestyProxyClient.preview(any(), any(), any(), any())
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
        // viewModel state now matches testHabit exactly (name, fullDescription, lowFloorDescription)

        viewModel.save()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockVariationRepository.deleteForHabit(any()) }
        coVerify(exactly = 0) { mockRefillScheduler.enqueueForHabit(any()) }
        assertTrue(viewModel.uiState.value.isSaved)
    }

    // --- On-device fallback ---

    @Test
    fun `previewNotification falls back to on-device when workerUrl is blank`() = runTest(testDispatcher) {
        // Default setup has empty URL/secret — should use promptGenerator
        coEvery { mockPromptGenerator.previewHabitNotification(any(), any()) } returns "On-device preview"

        viewModel.previewNotification()
        advanceUntilIdle()

        assertEquals("On-device preview", viewModel.uiState.value.previewNotification)
        assertTrue(viewModel.uiState.value.showPreviewDialog)
        coVerify(exactly = 0) { mockRequestyProxyClient.preview(any(), any(), any(), any()) }
    }

    @Test
    fun `autofillWithAi falls back to on-device when workerUrl is blank`() = runTest(testDispatcher) {
        // Default setup has empty URL/secret — should use promptGenerator
        coEvery { mockPromptGenerator.generateHabitFields("meditation") } returns
            AiHabitFields("On-device full", "On-device low")

        viewModel.autofillWithAi()
        advanceUntilIdle()

        assertEquals("On-device full", viewModel.uiState.value.fullDescription)
        assertEquals("On-device low", viewModel.uiState.value.lowFloorDescription)
        coVerify(exactly = 0) { mockRequestyProxyClient.habitFields(any(), any(), any()) }
    }

    // --- aiStatus cloud-aware derivation ---

    @Test
    fun `aiStatus is Unavailable when cloud flag ON and worker url blank`() = runTest(testDispatcher) {
        every { mockFeatureFlagsRepository.useCloudPool } returns flowOf(true)
        every { mockWorkerSettingsRepository.effectiveWorkerUrl } returns flowOf("")
        every { mockWorkerSettingsRepository.effectiveWorkerSecret } returns flowOf("")
        val vm = HabitEditViewModel(
            mockHabitRepository, mockLocationRepository, mockPromptGenerator,
            mockRequestyProxyClient, mockWorkerSettingsRepository, mockRefillScheduler,
            mockVariationRepository, mockFeatureFlagsRepository,
        )
        val job = launch { vm.aiStatus.collect {} }
        advanceUntilIdle()
        assertEquals(AiStatus.Unavailable, vm.aiStatus.value)
        job.cancel()
    }

    @Test
    fun `aiStatus is Unavailable when cloud ON and url blank but secret non-blank`() = runTest(testDispatcher) {
        every { mockFeatureFlagsRepository.useCloudPool } returns flowOf(true)
        every { mockWorkerSettingsRepository.effectiveWorkerUrl } returns flowOf("")
        every { mockWorkerSettingsRepository.effectiveWorkerSecret } returns flowOf("secret")
        val vm = HabitEditViewModel(
            mockHabitRepository, mockLocationRepository, mockPromptGenerator,
            mockRequestyProxyClient, mockWorkerSettingsRepository, mockRefillScheduler,
            mockVariationRepository, mockFeatureFlagsRepository,
        )
        val job = launch { vm.aiStatus.collect {} }
        advanceUntilIdle()
        assertEquals(AiStatus.Unavailable, vm.aiStatus.value)
        job.cancel()
    }

    @Test
    fun `aiStatus is Unavailable when cloud ON and secret blank but url non-blank`() = runTest(testDispatcher) {
        every { mockFeatureFlagsRepository.useCloudPool } returns flowOf(true)
        every { mockWorkerSettingsRepository.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
        every { mockWorkerSettingsRepository.effectiveWorkerSecret } returns flowOf("")
        val vm = HabitEditViewModel(
            mockHabitRepository, mockLocationRepository, mockPromptGenerator,
            mockRequestyProxyClient, mockWorkerSettingsRepository, mockRefillScheduler,
            mockVariationRepository, mockFeatureFlagsRepository,
        )
        val job = launch { vm.aiStatus.collect {} }
        advanceUntilIdle()
        assertEquals(AiStatus.Unavailable, vm.aiStatus.value)
        job.cancel()
    }

    // --- deleteVariation ---

    @Test
    fun `deleteVariation delegates to repository`() = runTest(testDispatcher) {
        viewModel.deleteVariation(42L)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockVariationRepository.deleteById(42L) }
    }

    @Test
    fun `deleteVariation swallows non-cancellation exceptions`() = runTest(testDispatcher) {
        coEvery { mockVariationRepository.deleteById(any()) } throws RuntimeException("DB error")

        viewModel.deleteVariation(42L)
        advanceUntilIdle()

        // No crash, no error state change — silently logged
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `aiStatus is Ready when cloud ON and worker configured`() = runTest(testDispatcher) {
        every { mockFeatureFlagsRepository.useCloudPool } returns flowOf(true)
        every { mockWorkerSettingsRepository.effectiveWorkerUrl } returns flowOf("https://worker.example.com")
        every { mockWorkerSettingsRepository.effectiveWorkerSecret } returns flowOf("secret")
        val vm = HabitEditViewModel(
            mockHabitRepository, mockLocationRepository, mockPromptGenerator,
            mockRequestyProxyClient, mockWorkerSettingsRepository, mockRefillScheduler,
            mockVariationRepository, mockFeatureFlagsRepository,
        )
        val job = launch { vm.aiStatus.collect {} }
        advanceUntilIdle()
        assertEquals(AiStatus.Ready, vm.aiStatus.value)
        job.cancel()
    }
}
