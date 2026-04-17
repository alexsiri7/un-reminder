package com.alexsiri7.unreminder.ui.location

import com.alexsiri7.unreminder.data.repository.LocationRepository
import com.alexsiri7.unreminder.service.geofence.GeofenceManager
import io.mockk.coEvery
import io.mockk.coVerify
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
        coEvery { locationRepository.getAll() } returns flowOf(emptyList())
        viewModel = LocationViewModel(locationRepository, geofenceManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `delete calls deleteByLabel and removeGeofence`() = runTest {
        coEvery { locationRepository.deleteByLabel("Home") } returns Unit
        coEvery { geofenceManager.removeGeofence("Home") } returns Unit

        viewModel.delete("Home")

        coVerify { locationRepository.deleteByLabel("Home") }
        coVerify { geofenceManager.removeGeofence("Home") }
    }
}
