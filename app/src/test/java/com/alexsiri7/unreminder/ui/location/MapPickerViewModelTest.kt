package com.alexsiri7.unreminder.ui.location

import android.content.Context
import com.alexsiri7.unreminder.data.db.LocationEntity
import com.alexsiri7.unreminder.data.repository.LocationRepository
import com.alexsiri7.unreminder.service.geofence.GeofenceManager
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
        val context = mockk<Context>(relaxed = true)
        viewModel = MapPickerViewModel(locationRepository, geofenceManager, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty name and default radius 100m`() {
        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertEquals(100f, state.radiusM)
        assertFalse(state.isSaved)
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
    fun `updatePin updates lat lng`() {
        viewModel.updatePin(51.5, -0.1)
        val state = viewModel.uiState.value
        assertEquals(51.5, state.lat, 0.0001)
        assertEquals(-0.1, state.lng, 0.0001)
    }

    @Test
    fun `save calls upsertLocation and registerGeofence`() = runTest {
        coEvery { locationRepository.upsertLocation(any(), any(), any(), any()) } returns Unit
        coEvery { geofenceManager.registerGeofence(any(), any(), any(), any()) } returns Unit

        viewModel.updateName("Home")
        viewModel.updatePin(51.5, -0.1)
        viewModel.updateRadius(150f)

        var callbackFired = false
        viewModel.save { callbackFired = true }

        coVerify { locationRepository.upsertLocation("Home", 51.5, -0.1, 150f) }
        coVerify { geofenceManager.registerGeofence("Home", 51.5, -0.1, 150f) }
        assertTrue(callbackFired)
    }

    @Test
    fun `save does nothing when name is blank`() = runTest {
        viewModel.updateName("")
        viewModel.save { fail("Should not call onComplete") }
        coVerify(exactly = 0) { locationRepository.upsertLocation(any(), any(), any(), any()) }
    }

    @Test
    fun `initialize with existing label pre-populates state`() = runTest {
        coEvery { locationRepository.getByLabel("Office") } returns
            LocationEntity(id = 1, label = "Office", lat = 48.8, lng = 2.3, radiusM = 200f)

        viewModel.initialize("Office")

        val state = viewModel.uiState.value
        assertEquals("Office", state.name)
        assertEquals(48.8, state.lat, 0.0001)
        assertEquals(200f, state.radiusM)
        assertTrue(state.centerReady)
    }
}
