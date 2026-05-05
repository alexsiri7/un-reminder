package net.interstellarai.unreminder.ui.settings

import android.app.AlarmManager
import android.content.Context
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.trigger.TriggerPipeline
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var triggerRepository: TriggerRepository
    private lateinit var triggerPipeline: TriggerPipeline
    private lateinit var habitRepository: HabitRepository
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var context: Context
    private lateinit var viewModel: SettingsViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val currentLocationIdsFlow = MutableStateFlow<Set<Long>>(emptySet())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        triggerRepository = mockk(relaxUnitFun = true)
        triggerPipeline = mockk(relaxUnitFun = true)
        habitRepository = mockk(relaxUnitFun = true)
        geofenceManager = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.getSystemService(Context.ALARM_SERVICE) } returns mockk<AlarmManager>(relaxed = true)
        currentLocationIdsFlow.value = emptySet()
        // Default: at least one eligible habit so the pre-existing tests still exercise the pipeline path.
        every { geofenceManager.currentLocationIds } returns currentLocationIdsFlow.asStateFlow()
        coEvery { habitRepository.getEligibleHabits(any()) } returns listOf(
            HabitEntity(id = 1L, name = "habit")
        )

        viewModel = SettingsViewModel(
            context = context,
            triggerPipeline = triggerPipeline,
            triggerRepository = triggerRepository,
            habitRepository = habitRepository,
            geofenceManager = geofenceManager,
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

    // --- testTriggerNow eligibility branches ---

    @Test
    fun `testTriggerNow sets testTriggeredEmpty and skips pipeline when no eligible habits`() = runTest {
<<<<<<< HEAD
        currentLocationIdsFlow.value = emptySet()
=======
        every { geofenceManager.currentLocationIds } returns MutableStateFlow<Set<Long>>(emptySet()).asStateFlow()
>>>>>>> ff24e5a (Fix: persist geofence location state and reactively update habit availability badge (#245))
        coEvery { habitRepository.getEligibleHabits(any()) } returns emptyList()

        viewModel.testTriggerNow()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.testTriggeredEmpty)
        assertFalse(viewModel.uiState.value.testTriggered)
        coVerify(exactly = 0) { triggerRepository.insert(any()) }
        coVerify(exactly = 0) { triggerPipeline.execute(any()) }
    }

    @Test
    fun `testTriggerNow fires pipeline when at least one eligible habit`() = runTest {
<<<<<<< HEAD
        currentLocationIdsFlow.value = setOf(7L)
=======
        every { geofenceManager.currentLocationIds } returns MutableStateFlow<Set<Long>>(setOf(7L)).asStateFlow()
>>>>>>> ff24e5a (Fix: persist geofence location state and reactively update habit availability badge (#245))
        coEvery { habitRepository.getEligibleHabits(setOf(7L)) } returns listOf(
            HabitEntity(id = 1L, name = "habit")
        )
        coEvery { triggerRepository.insert(any()) } returns 42L

        viewModel.testTriggerNow()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.testTriggered)
        assertFalse(viewModel.uiState.value.testTriggeredEmpty)
        coVerify { triggerPipeline.execute(42L) }
    }

    @Test
    fun `clearTestTriggered resets testTriggered to false`() = runTest {
        coEvery { triggerRepository.insert(any()) } returns 1L
        viewModel.testTriggerNow()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.testTriggered)

        viewModel.clearTestTriggered()
        assertFalse(viewModel.uiState.value.testTriggered)
    }

    @Test
    fun `clearTestTriggeredEmpty resets testTriggeredEmpty to false`() = runTest {
<<<<<<< HEAD
        currentLocationIdsFlow.value = emptySet()
=======
        every { geofenceManager.currentLocationIds } returns MutableStateFlow<Set<Long>>(emptySet()).asStateFlow()
>>>>>>> ff24e5a (Fix: persist geofence location state and reactively update habit availability badge (#245))
        coEvery { habitRepository.getEligibleHabits(any()) } returns emptyList()
        viewModel.testTriggerNow()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.testTriggeredEmpty)

        viewModel.clearTestTriggeredEmpty()
        assertFalse(viewModel.uiState.value.testTriggeredEmpty)
    }
}
