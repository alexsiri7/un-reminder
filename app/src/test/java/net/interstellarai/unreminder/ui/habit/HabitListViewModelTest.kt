package net.interstellarai.unreminder.ui.habit

import net.interstellarai.unreminder.data.repository.GenerationFailure
import net.interstellarai.unreminder.data.repository.GenerationFailureRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.llm.AiStatus
import net.interstellarai.unreminder.service.llm.PromptGenerator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HabitListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockPromptGenerator: PromptGenerator = mockk()
    private val mockHabitRepository: HabitRepository = mockk()
    private val mockAvailabilityService: HabitAvailabilityService = mockk()
    private val mockGeofenceManager: GeofenceManager = mockk()
    private val storedFailure = MutableStateFlow<GenerationFailure?>(null)
    private val mockGenerationFailureRepository: GenerationFailureRepository = mockk {
        every { failure } returns storedFailure
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { mockHabitRepository.getAll() } returns flowOf(emptyList())
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow(AiStatus.Ready)
        every { mockGeofenceManager.currentLocationIds } returns MutableStateFlow(emptySet())
        coEvery { mockAvailabilityService.computeForAll(any()) } returns emptyMap()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = HabitListViewModel(
        mockHabitRepository,
        mockPromptGenerator,
        mockAvailabilityService,
        mockGeofenceManager,
        mockGenerationFailureRepository,
    )

    @Test
    fun `aiStatus delegates directly to promptGenerator aiStatus`() = runTest(testDispatcher) {
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow(AiStatus.Unavailable)
        val vm = buildViewModel()
        assertEquals(AiStatus.Unavailable, vm.aiStatus.value)
    }

    @Test
    fun `aiStatus is Ready when promptGenerator reports Ready`() = runTest(testDispatcher) {
        every { mockPromptGenerator.aiStatus } returns MutableStateFlow(AiStatus.Ready)
        val vm = buildViewModel()
        assertEquals(AiStatus.Ready, vm.aiStatus.value)
    }

    @Test
    fun `tokenRejected is true only while the last failure is a rejected token`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        backgroundScope.launch { vm.tokenRejected.collect {} }
        advanceUntilIdle()
        assertFalse(vm.tokenRejected.value)

        storedFailure.value = GenerationFailure(GenerationFailure.Kind.TOKEN_REJECTED, null, Instant.EPOCH)
        advanceUntilIdle()
        assertTrue(vm.tokenRejected.value)

        storedFailure.value = GenerationFailure(GenerationFailure.Kind.SPEND_CAP_USER, null, Instant.EPOCH)
        advanceUntilIdle()
        assertFalse(vm.tokenRejected.value)

        storedFailure.value = null
        advanceUntilIdle()
        assertFalse(vm.tokenRejected.value)
    }
}
