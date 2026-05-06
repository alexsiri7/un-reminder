package net.interstellarai.unreminder.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.PersonalContextRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.trigger.TriggerPipeline
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private lateinit var personalContextRepository: PersonalContextRepository
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
        personalContextRepository = mockk(relaxUnitFun = true)
        every { personalContextRepository.personalContext } returns flowOf("")
        context = mockk(relaxed = true)
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
            personalContextRepository = personalContextRepository,
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
        currentLocationIdsFlow.value = emptySet()
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
        currentLocationIdsFlow.value = setOf(7L)
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
        currentLocationIdsFlow.value = emptySet()
        coEvery { habitRepository.getEligibleHabits(any()) } returns emptyList()
        viewModel.testTriggerNow()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.testTriggeredEmpty)

        viewModel.clearTestTriggeredEmpty()
        assertFalse(viewModel.uiState.value.testTriggeredEmpty)
    }

    @Test
    fun `refreshPermissions sets each permission flag from ContextCompat result`() {
        mockkStatic(ContextCompat::class)
        try {
            every {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            } returns PackageManager.PERMISSION_GRANTED
            every {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            } returns PackageManager.PERMISSION_DENIED
            every {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } returns PackageManager.PERMISSION_DENIED

            viewModel.refreshPermissions()

            assertTrue(viewModel.uiState.value.hasNotificationPermission)
            assertFalse(viewModel.uiState.value.hasFineLocationPermission)
            assertFalse(viewModel.uiState.value.hasBackgroundLocationPermission)
        } finally {
            unmockkStatic(ContextCompat::class)
        }
    }

    @Test
    fun `refreshPermissions sets all flags granted when all permissions granted`() {
        mockkStatic(ContextCompat::class)
        try {
            every {
                ContextCompat.checkSelfPermission(context, any())
            } returns PackageManager.PERMISSION_GRANTED

            viewModel.refreshPermissions()

            assertTrue(viewModel.uiState.value.hasNotificationPermission)
            assertTrue(viewModel.uiState.value.hasFineLocationPermission)
            assertTrue(viewModel.uiState.value.hasBackgroundLocationPermission)
        } finally {
            unmockkStatic(ContextCompat::class)
        }
    }

    // --- personalContext ---

    @Test
    fun `personalContext initializes from repository`() = runTest {
        every { personalContextRepository.personalContext } returns flowOf("encouragement")
        val vm = SettingsViewModel(
            context = context,
            triggerPipeline = triggerPipeline,
            triggerRepository = triggerRepository,
            habitRepository = habitRepository,
            geofenceManager = geofenceManager,
            personalContextRepository = personalContextRepository,
        )
        advanceUntilIdle()
        assertEquals("encouragement", vm.uiState.value.personalContext)
    }

    @Test
    fun `setPersonalContext persists to repository`() = runTest {
        viewModel.setPersonalContext("give metrics")
        advanceUntilIdle()
        coVerify { personalContextRepository.setPersonalContext("give metrics") }
    }

    @Test
    fun `setPersonalContext truncates at 500 chars`() = runTest {
        viewModel.setPersonalContext("x".repeat(600))
        advanceUntilIdle()
        coVerify { personalContextRepository.setPersonalContext("x".repeat(500)) }
    }

    @Test
    fun `refreshPermissions preserves personalContext`() = runTest {
        every { personalContextRepository.personalContext } returns flowOf("use metrics")
        val vm = SettingsViewModel(
            context = context,
            triggerPipeline = triggerPipeline,
            triggerRepository = triggerRepository,
            habitRepository = habitRepository,
            geofenceManager = geofenceManager,
            personalContextRepository = personalContextRepository,
        )
        advanceUntilIdle()

        mockkStatic(ContextCompat::class)
        try {
            every { ContextCompat.checkSelfPermission(context, any()) } returns PackageManager.PERMISSION_DENIED
            vm.refreshPermissions()
        } finally {
            unmockkStatic(ContextCompat::class)
        }

        assertEquals("use metrics", vm.uiState.value.personalContext)
    }
}
