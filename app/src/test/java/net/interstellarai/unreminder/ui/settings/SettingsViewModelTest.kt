package net.interstellarai.unreminder.ui.settings

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.repository.EveningInvitationRepository
import net.interstellarai.unreminder.data.repository.EveningInvitationSettings
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.PersonalContextRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.geofence.GeofenceRegistration
import net.interstellarai.unreminder.service.geofence.LocationSettingsCheck
import net.interstellarai.unreminder.service.geofence.RegistrationHealth
import net.interstellarai.unreminder.service.trigger.TriggerPipeline
import net.interstellarai.unreminder.worker.EveningInvitationScheduler
import com.google.android.gms.location.GeofenceStatusCodes
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
import java.time.Instant
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var triggerRepository: TriggerRepository
    private lateinit var triggerPipeline: TriggerPipeline
    private lateinit var habitRepository: HabitRepository
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var personalContextRepository: PersonalContextRepository
    private lateinit var eveningInvitationRepository: EveningInvitationRepository
    private lateinit var eveningInvitationScheduler: EveningInvitationScheduler
    private lateinit var context: Context
    private lateinit var viewModel: SettingsViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val currentLocationIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    private val registrationHealthFlow = MutableStateFlow<RegistrationHealth?>(null)
    private val activityManager: ActivityManager = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        triggerRepository = mockk(relaxUnitFun = true)
        triggerPipeline = mockk(relaxUnitFun = true)
        habitRepository = mockk(relaxUnitFun = true)
        geofenceManager = mockk(relaxed = true)
        personalContextRepository = mockk(relaxUnitFun = true)
        every { personalContextRepository.personalContext } returns flowOf("")
        eveningInvitationRepository = mockk(relaxUnitFun = true)
        every { eveningInvitationRepository.settings } returns flowOf(EveningInvitationSettings())
        eveningInvitationScheduler = mockk(relaxUnitFun = true)
        context = mockk(relaxed = true)
        every { context.getSystemService(ActivityManager::class.java) } returns activityManager
        every { activityManager.isBackgroundRestricted } returns false
        currentLocationIdsFlow.value = emptySet()
        registrationHealthFlow.value = null
        // Default: at least one eligible habit so the pre-existing tests still exercise the pipeline path.
        every { geofenceManager.currentLocationIds } returns currentLocationIdsFlow.asStateFlow()
        every { geofenceManager.registrationHealth } returns registrationHealthFlow.asStateFlow()
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
            eveningInvitationRepository = eveningInvitationRepository,
            eveningInvitationScheduler = eveningInvitationScheduler,
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

    // --- location tracking health ---

    private fun health(
        outcomes: List<Pair<Long, GeofenceRegistration>>,
        backgroundLocationGranted: Boolean = true,
        locationEnabled: Boolean? = true,
        locationSettings: LocationSettingsCheck = LocationSettingsCheck.Available,
    ) = RegistrationHealth(
        outcomes = outcomes,
        fineLocationGranted = true,
        backgroundLocationGranted = backgroundLocationGranted,
        locationEnabled = locationEnabled,
        locationSettings = locationSettings,
        checkedAt = Instant.parse("2026-09-11T09:41:00Z"),
    )

    @Test
    fun `location tracking reads as checking until the manager reports a registration`() = runTest {
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.registrationHealth)
        assertEquals(LocationTrackingStatus.Checking, viewModel.uiState.value.locationTracking)
    }

    @Test
    fun `uiState exposes registration counts and health from the manager`() = runTest {
        val reported = health(
            outcomes = listOf(
                1L to GeofenceRegistration.Registered,
                2L to GeofenceRegistration.Rejected(GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES),
            ),
        )
        registrationHealthFlow.value = reported
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(reported, state.registrationHealth)
        assertEquals(1, state.registrationHealth!!.registeredCount)
        assertEquals(2, state.registrationHealth!!.savedCount)
        assertEquals(
            LocationTrackingStatus.RegistrationFailed("GEOFENCE_TOO_MANY_GEOFENCES(1001)"),
            state.locationTracking,
        )
    }

    @Test
    fun `no saved locations reads as neutral rather than a fault`() = runTest {
        registrationHealthFlow.value = health(outcomes = emptyList(), backgroundLocationGranted = false)
        advanceUntilIdle()

        val status = viewModel.uiState.value.locationTracking
        assertEquals(LocationTrackingStatus.NoLocations, status)
        assertFalse(status.isFault)
        assertNull(status.advice)
    }

    @Test
    fun `refreshPermissions reads background restriction and it surfaces as a battery fault`() = runTest {
        registrationHealthFlow.value = health(outcomes = listOf(1L to GeofenceRegistration.Registered))
        every { activityManager.isBackgroundRestricted } returns true
        mockkStatic(ContextCompat::class)
        try {
            every { ContextCompat.checkSelfPermission(context, any()) } returns PackageManager.PERMISSION_GRANTED
            viewModel.refreshPermissions()
        } finally {
            unmockkStatic(ContextCompat::class)
        }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.backgroundRestricted)
        assertEquals(LocationTrackingStatus.BatteryRestricted, viewModel.uiState.value.locationTracking)
    }

    @Test
    fun `a fully registered set with no restriction reads healthy`() = runTest {
        registrationHealthFlow.value = health(outcomes = listOf(1L to GeofenceRegistration.Registered))
        advanceUntilIdle()

        assertEquals(LocationTrackingStatus.Healthy, viewModel.uiState.value.locationTracking)
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
            eveningInvitationRepository = eveningInvitationRepository,
            eveningInvitationScheduler = eveningInvitationScheduler,
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

    // --- evening invitation ---

    @Test
    fun `evening invitation settings initialize from repository`() = runTest {
        every { eveningInvitationRepository.settings } returns flowOf(
            EveningInvitationSettings(enabled = false, time = LocalTime.of(21, 15))
        )
        val vm = SettingsViewModel(
            context = context,
            triggerPipeline = triggerPipeline,
            triggerRepository = triggerRepository,
            habitRepository = habitRepository,
            geofenceManager = geofenceManager,
            personalContextRepository = personalContextRepository,
            eveningInvitationRepository = eveningInvitationRepository,
            eveningInvitationScheduler = eveningInvitationScheduler,
        )
        advanceUntilIdle()
        assertFalse(vm.uiState.value.eveningInvitationEnabled)
        assertEquals(LocalTime.of(21, 15), vm.uiState.value.eveningInvitationTime)
    }

    @Test
    fun `setEveningInvitationEnabled persists and reschedules`() = runTest {
        viewModel.setEveningInvitationEnabled(false)
        advanceUntilIdle()
        coVerify { eveningInvitationRepository.setEnabled(false) }
        coVerify { eveningInvitationScheduler.reschedule() }
    }

    @Test
    fun `setEveningInvitationTime persists and reschedules`() = runTest {
        viewModel.setEveningInvitationTime(LocalTime.of(19, 45))
        advanceUntilIdle()
        coVerify { eveningInvitationRepository.setTime(LocalTime.of(19, 45)) }
        coVerify { eveningInvitationScheduler.reschedule() }
    }
}
