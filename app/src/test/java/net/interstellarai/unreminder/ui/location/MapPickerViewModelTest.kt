package net.interstellarai.unreminder.ui.location

import android.content.Context
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.geofence.GeofenceRegistration
import net.interstellarai.unreminder.service.geofence.GeofenceManager.Companion.MIN_RADIUS_M
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapPickerViewModelTest {

    private lateinit var locationRepository: LocationRepository
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var viewModel: MapPickerViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        locationRepository = mockk(relaxUnitFun = true)
        geofenceManager = mockk(relaxUnitFun = true)
        coEvery { geofenceManager.registerGeofence(any(), any(), any(), any(), any()) } returns GeofenceRegistration.Registered
        val context = mockk<Context>(relaxed = true)
        viewModel = MapPickerViewModel(locationRepository, geofenceManager, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty name and the minimum radius`() {
        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertEquals(MIN_RADIUS_M, state.radiusM)
        assertFalse(state.centerReady)
    }

    @Test
    fun `updateName updates state`() {
        viewModel.updateName("Gym")
        assertEquals("Gym", viewModel.uiState.value.name)
    }

    @Test
    fun `updateRadius updates state`() {
        viewModel.updateRadius(250f)
        assertEquals(250f, viewModel.uiState.value.radiusM)
    }

    @Test
    fun `updateRadius clamps a sub-minimum radius up to 100m`() {
        viewModel.updateRadius(40f)
        assertEquals(100f, viewModel.uiState.value.radiusM)
    }

    @Test
    fun `updateRadius accepts exactly 100m unchanged`() {
        viewModel.updateRadius(100f)
        assertEquals(100f, viewModel.uiState.value.radiusM)
    }

    @Test
    fun `save cannot persist a radius below 100m`() = runTest {
        coEvery { locationRepository.upsertLocation(any(), any(), any(), any()) } returns 7L

        viewModel.updateName("Home")
        viewModel.updatePin(51.5, -0.1)
        viewModel.updateRadius(40f)
        viewModel.save {}

        coVerify { locationRepository.upsertLocation("Home", 51.5, -0.1, 100f) }
        coVerify { geofenceManager.registerGeofence(7L, "Home", 51.5, -0.1, 100f) }
    }

    @Test
    fun `initialize raises a stored sub-minimum radius so the picker never shows it`() = runTest {
        coEvery { locationRepository.getByName("Shed") } returns
            LocationEntity(id = 2, name = "Shed", lat = 48.8, lng = 2.3, radiusM = 40f)

        viewModel.initialize("Shed")

        assertEquals(100f, viewModel.uiState.value.radiusM)
    }

    @Test
    fun `updatePin updates lat lng`() {
        viewModel.updatePin(51.5, -0.1)
        val state = viewModel.uiState.value
        assertEquals(51.5, state.lat, 0.0001)
        assertEquals(-0.1, state.lng, 0.0001)
    }

    @Test
    fun `save calls upsertLocation and registerGeofence`() = runTest {
        val savedId = 7L
        coEvery { locationRepository.upsertLocation(any(), any(), any(), any()) } returns savedId

        viewModel.updateName("Home")
        viewModel.updatePin(51.5, -0.1)
        viewModel.updateRadius(150f)

        var callbackFired = false
        viewModel.save { callbackFired = true }

        coVerify { locationRepository.upsertLocation("Home", 51.5, -0.1, 150f) }
        coVerify { geofenceManager.registerGeofence(savedId, "Home", 51.5, -0.1, 150f) }
        assertTrue(callbackFired)
    }

    @Test
    fun `save does nothing when name is blank`() = runTest {
        viewModel.updateName("")
        viewModel.save { fail("Should not call onComplete") }
        coVerify(exactly = 0) { locationRepository.upsertLocation(any(), any(), any(), any()) }
    }

    @Test
    fun `initialize with existing name pre-populates state`() = runTest {
        coEvery { locationRepository.getByName("Office") } returns
            LocationEntity(id = 1, name = "Office", lat = 48.8, lng = 2.3, radiusM = 200f)

        viewModel.initialize("Office")

        val state = viewModel.uiState.value
        assertEquals("Office", state.name)
        assertEquals(48.8, state.lat, 0.0001)
        assertEquals(200f, state.radiusM)
        assertTrue(state.centerReady)
    }

    @Test
    fun `save does not invoke onComplete and sets errorMessage when upsertLocation throws`() = runTest {
        coEvery {
            locationRepository.upsertLocation(any(), any(), any(), any())
        } throws RuntimeException("DB error")

        viewModel.updateName("Gym")
        var callbackFired = false
        viewModel.save { callbackFired = true }

        assertFalse(callbackFired)
        assertFalse(viewModel.uiState.value.errorMessage.isNullOrBlank())
    }
}
