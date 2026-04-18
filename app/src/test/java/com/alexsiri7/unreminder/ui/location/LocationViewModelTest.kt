package com.alexsiri7.unreminder.ui.location

import com.alexsiri7.unreminder.data.db.LocationEntity
import com.alexsiri7.unreminder.data.repository.LocationRepository
import com.alexsiri7.unreminder.service.geofence.GeofenceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {

    private lateinit var locationRepository: LocationRepository
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var viewModel: LocationViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        locationRepository = mockk(relaxUnitFun = true)
        geofenceManager = mockk(relaxUnitFun = true)
        every { locationRepository.getAll() } returns flowOf(emptyList())
        viewModel = LocationViewModel(locationRepository, geofenceManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `deleteLocation calls delete on repository and removeGeofence by id`() = runTest {
        val location = LocationEntity(id = 42L, name = "Home", lat = 51.5, lng = -0.1, radiusM = 100f)
        coEvery { locationRepository.delete(location) } returns Unit
        coEvery { geofenceManager.removeGeofence(42L) } returns Unit

        viewModel.deleteLocation(location)

        coVerify { locationRepository.delete(location) }
        coVerify { geofenceManager.removeGeofence(42L) }
    }
}
