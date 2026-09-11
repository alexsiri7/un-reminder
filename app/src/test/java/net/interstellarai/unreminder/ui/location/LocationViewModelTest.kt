package net.interstellarai.unreminder.ui.location

import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.service.geofence.GeofenceManager
import net.interstellarai.unreminder.service.geofence.LocationCheck
import net.interstellarai.unreminder.service.geofence.LocationReconciler
import net.interstellarai.unreminder.service.geofence.LocationSetChangeCause
import net.interstellarai.unreminder.service.geofence.Reconciliation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {

    private lateinit var locationRepository: LocationRepository
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var locationReconciler: LocationReconciler
    private lateinit var viewModel: LocationViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val locationsFlow = MutableStateFlow<List<LocationEntity>>(emptyList())
    private val checksFlow = MutableStateFlow<Map<Long, LocationCheck>>(emptyMap())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        locationRepository = mockk(relaxUnitFun = true)
        geofenceManager = mockk(relaxUnitFun = true)
        locationReconciler = mockk()
        every { locationRepository.getAll() } returns locationsFlow
        every { geofenceManager.locationChecks } returns checksFlow
        viewModel = LocationViewModel(locationRepository, geofenceManager, locationReconciler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `deleteLocation calls delete on repository and removeGeofence by id, then refreshes registration health`() = runTest(testDispatcher) {
        val location = LocationEntity(id = 42L, name = "Home", lat = 51.5, lng = -0.1, radiusM = 100f)
        coEvery { locationRepository.delete(location) } returns Unit
        coEvery { geofenceManager.removeGeofence(42L, "Home") } returns Unit

        viewModel.deleteLocation(location)
        advanceUntilIdle()

        coVerify { locationRepository.delete(location) }
        coVerify { geofenceManager.removeGeofence(42L, "Home") }
        verify(exactly = 1) { geofenceManager.refreshRegistration() }
    }

    @Test
    fun `deleteLocation refreshes registration health only once the geofence removal has settled`() = runTest(testDispatcher) {
        val location = LocationEntity(id = 42L, name = "Home", lat = 51.5, lng = -0.1, radiusM = 100f)
        val removalSettled = CompletableDeferred<Unit>()
        coEvery { locationRepository.delete(location) } returns Unit
        coEvery { geofenceManager.removeGeofence(42L, "Home") } coAnswers { removalSettled.await() }

        viewModel.deleteLocation(location)
        advanceUntilIdle()

        verify(exactly = 0) { geofenceManager.refreshRegistration() }

        removalSettled.complete(Unit)
        advanceUntilIdle()

        verify(exactly = 1) { geofenceManager.refreshRegistration() }
    }

    @Test
    fun `locations exposes empty list when no data`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.locations.collect {} }
        advanceUntilIdle()

        assertEquals(emptyList<LocationRow>(), viewModel.locations.value)
    }

    @Test
    fun `locations reads presence from the check recorded for each location`() = runTest(testDispatcher) {
        locationsFlow.value = listOf(home, office)
        checksFlow.value = mapOf(home.id to insideCheck, office.id to outsideCheck)

        backgroundScope.launch { viewModel.locations.collect {} }
        advanceUntilIdle()

        val rows = viewModel.locations.value
        assertEquals(2, rows.size)
        assertEquals(home, rows[0].location)
        assertEquals(LocationPresence.INSIDE, rows[0].presence)
        assertEquals(office, rows[1].location)
        assertEquals(LocationPresence.OUTSIDE, rows[1].presence)
    }

    @Test
    fun `a location never checked is unknown rather than outside`() = runTest(testDispatcher) {
        locationsFlow.value = listOf(home, office)
        checksFlow.value = mapOf(office.id to outsideCheck)

        backgroundScope.launch { viewModel.locations.collect {} }
        advanceUntilIdle()

        assertEquals(LocationPresence.UNKNOWN, viewModel.locations.value[0].presence)
        assertEquals(LocationPresence.OUTSIDE, viewModel.locations.value[1].presence)
    }

    @Test
    fun `locations updates reactively when the checks change`() = runTest(testDispatcher) {
        locationsFlow.value = listOf(home, office)
        checksFlow.value = mapOf(home.id to insideCheck)

        backgroundScope.launch { viewModel.locations.collect {} }
        advanceUntilIdle()

        assertEquals(LocationPresence.INSIDE, viewModel.locations.value[0].presence)
        assertEquals(LocationPresence.UNKNOWN, viewModel.locations.value[1].presence)

        checksFlow.value = mapOf(home.id to outsideCheck, office.id to insideCheck)
        advanceUntilIdle()

        assertEquals(LocationPresence.OUTSIDE, viewModel.locations.value[0].presence)
        assertEquals(LocationPresence.INSIDE, viewModel.locations.value[1].presence)
    }

    @Test
    fun `recalculate runs a forced reconciliation and reports progress then idle`() = runTest(testDispatcher) {
        val reconciled = CompletableDeferred<Reconciliation>()
        coEvery { locationReconciler.reconcileNow() } coAnswers { reconciled.await() }

        viewModel.recalculate()
        advanceUntilIdle()
        assertEquals(RecalculationState.Running, viewModel.recalculation.value)

        reconciled.complete(Reconciliation.Reconciled)
        advanceUntilIdle()

        assertEquals(RecalculationState.Idle, viewModel.recalculation.value)
        coVerify(exactly = 1) { locationReconciler.reconcileNow() }
    }

    @Test
    fun `a failed recalculation names the reason and leaves the rows alone`() = runTest(testDispatcher) {
        locationsFlow.value = listOf(home)
        checksFlow.value = mapOf(home.id to insideCheck)
        coEvery { locationReconciler.reconcileNow() } returns Reconciliation.NoFix
        backgroundScope.launch { viewModel.locations.collect {} }
        advanceUntilIdle()

        viewModel.recalculate()
        advanceUntilIdle()

        val state = viewModel.recalculation.value
        assertTrue(state is RecalculationState.Failed)
        assertTrue((state as RecalculationState.Failed).label.isNotBlank())
        assertEquals(listOf(LocationRow(home, insideCheck)), viewModel.locations.value)
    }

    @Test
    fun `a second recalculate while the first is still running is ignored`() = runTest(testDispatcher) {
        val reconciled = CompletableDeferred<Reconciliation>()
        coEvery { locationReconciler.reconcileNow() } coAnswers { reconciled.await() }

        viewModel.recalculate()
        advanceUntilIdle()
        viewModel.recalculate()
        advanceUntilIdle()

        coVerify(exactly = 1) { locationReconciler.reconcileNow() }

        reconciled.complete(Reconciliation.Reconciled)
        advanceUntilIdle()
    }

    private companion object {
        val home = LocationEntity(id = 1L, name = "Home", lat = 51.5, lng = -0.1, radiusM = 100f)
        val office = LocationEntity(id = 2L, name = "Office", lat = 51.5, lng = -0.09, radiusM = 100f)
        val insideCheck = LocationCheck(
            inside = true,
            at = Instant.parse("2026-09-11T09:41:00Z"),
            via = LocationSetChangeCause.ENTER,
        )
        val outsideCheck = LocationCheck(
            inside = false,
            at = Instant.parse("2026-09-11T09:41:00Z"),
            via = LocationSetChangeCause.RECONCILE,
        )
    }

    @Test
    fun `deleteLocation swallows repository failure and skips removeGeofence`() = runTest(testDispatcher) {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        try {
            val location = LocationEntity(id = 7L, name = "Home", lat = 51.5, lng = -0.1, radiusM = 100f)
            coEvery { locationRepository.delete(location) } throws RuntimeException("db down")

            viewModel.deleteLocation(location)
            advanceUntilIdle()

            coVerify(exactly = 1) { locationRepository.delete(location) }
            coVerify(exactly = 0) { geofenceManager.removeGeofence(any(), any()) }
            verify(exactly = 0) { geofenceManager.refreshRegistration() }
        } finally {
            unmockkStatic(android.util.Log::class)
        }
    }
}
