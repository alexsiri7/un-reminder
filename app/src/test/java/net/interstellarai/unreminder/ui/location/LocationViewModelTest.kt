package net.interstellarai.unreminder.ui.location

import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {

    private lateinit var locationRepository: LocationRepository
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var viewModel: LocationViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val locationsFlow = MutableStateFlow<List<LocationEntity>>(emptyList())
    private val currentIdsFlow = MutableStateFlow<Set<Long>>(emptySet())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        locationRepository = mockk(relaxUnitFun = true)
        geofenceManager = mockk(relaxUnitFun = true)
        every { locationRepository.getAll() } returns locationsFlow
        every { geofenceManager.currentLocationIds } returns currentIdsFlow
        viewModel = LocationViewModel(locationRepository, geofenceManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `deleteLocation calls delete on repository and removeGeofence by id`() = runTest(testDispatcher) {
        val location = LocationEntity(id = 42L, name = "Home", lat = 51.5, lng = -0.1, radiusM = 100f)
        coEvery { locationRepository.delete(location) } returns Unit
        coEvery { geofenceManager.removeGeofence(42L) } returns Unit

        viewModel.deleteLocation(location)
        advanceUntilIdle()

        coVerify { locationRepository.delete(location) }
        coVerify { geofenceManager.removeGeofence(42L) }
    }

    @Test
    fun `locations exposes empty list when no data`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.locations.collect {} }
        advanceUntilIdle()

        assertEquals(emptyList<LocationRow>(), viewModel.locations.value)
    }

    @Test
    fun `locations marks isCurrent=true when id matches currentLocationIds`() = runTest(testDispatcher) {
        val home = LocationEntity(id = 1L, name = "Home", lat = 51.5, lng = -0.1, radiusM = 100f)
        val office = LocationEntity(id = 2L, name = "Office", lat = 51.5, lng = -0.09, radiusM = 100f)
        locationsFlow.value = listOf(home, office)
        currentIdsFlow.value = setOf(1L)

        backgroundScope.launch { viewModel.locations.collect {} }
        advanceUntilIdle()

        val rows = viewModel.locations.value
        assertEquals(2, rows.size)
        assertEquals(home, rows[0].location)
        assertTrue(rows[0].isCurrent)
        assertEquals(office, rows[1].location)
        assertFalse(rows[1].isCurrent)
    }

    @Test
    fun `locations updates reactively when currentLocationIds changes`() = runTest(testDispatcher) {
        val home = LocationEntity(id = 1L, name = "Home", lat = 51.5, lng = -0.1, radiusM = 100f)
        val office = LocationEntity(id = 2L, name = "Office", lat = 51.5, lng = -0.09, radiusM = 100f)
        locationsFlow.value = listOf(home, office)
        currentIdsFlow.value = setOf(1L)

        backgroundScope.launch { viewModel.locations.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.locations.value[0].isCurrent)
        assertFalse(viewModel.locations.value[1].isCurrent)

        currentIdsFlow.value = setOf(2L)
        advanceUntilIdle()

        assertFalse(viewModel.locations.value[0].isCurrent)
        assertTrue(viewModel.locations.value[1].isCurrent)
    }
}
