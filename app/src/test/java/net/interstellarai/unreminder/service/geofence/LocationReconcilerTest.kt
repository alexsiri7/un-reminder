package net.interstellarai.unreminder.service.geofence

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.Breadcrumb
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.HabitEntity
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.LocationRepository
import net.interstellarai.unreminder.data.repository.TriggerRepository
import net.interstellarai.unreminder.data.repository.WindowRepository
import net.interstellarai.unreminder.domain.AvailabilityStatus
import net.interstellarai.unreminder.domain.HabitAvailabilityService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class LocationReconcilerTest {

    private lateinit var context: Context
    private val locationRepository: LocationRepository = mockk()
    private val fusedLocationClient: FusedLocationProviderClient = mockk()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()

        mockkStatic(Sentry::class)
        every { Sentry.addBreadcrumb(any<Breadcrumb>()) } just runs
        every { Sentry.captureException(any<Throwable>(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID

        every { fusedLocationClient.lastLocation } returns Tasks.forResult<Location>(null)
        coEvery { locationRepository.getAllList() } returns listOf(here, nearby, faraway)
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @After
    fun tearDown() {
        unmockkStatic(Sentry::class)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun newGeofenceManager() = GeofenceManager(
        context,
        locationRepository,
        mockk<GeofencingClient>(),
        mockk<SettingsClient>(),
        CoroutineScope(Dispatchers.Unconfined),
    )

    private fun newReconciler(geofenceManager: GeofenceManager) =
        LocationReconciler(context, locationRepository, geofenceManager, fusedLocationClient)

    private fun fixAt(lat: Double, lng: Double, ageMillis: Long = 0L) = Location("test").apply {
        latitude = lat
        longitude = lng
        time = System.currentTimeMillis() - ageMillis
    }

    private fun currentLocationReturns(location: Location?) {
        every {
            fusedLocationClient.getCurrentLocation(any<Int>(), any<CancellationToken>())
        } returns Tasks.forResult<Location>(location)
    }

    private fun currentLocationFailsWith(error: Exception) {
        every {
            fusedLocationClient.getCurrentLocation(any<Int>(), any<CancellationToken>())
        } returns Tasks.forException(error)
    }

    private fun currentLocationNeverSettles() {
        every {
            fusedLocationClient.getCurrentLocation(any<Int>(), any<CancellationToken>())
        } returns TaskCompletionSource<Location>().task
    }

    @Test
    fun `a fix inside a radius records that location when nothing was recorded`() = runTest {
        currentLocationReturns(fixAt(FIX_LAT, FIX_LNG))
        val geofenceManager = newGeofenceManager()

        newReconciler(geofenceManager).reconcile()

        assertEquals(setOf(here.id, nearby.id), geofenceManager.currentLocationIds.value)
    }

    @Test
    fun `a fix outside drops a location the set wrongly held`() = runTest {
        currentLocationReturns(fixAt(FIX_LAT, FIX_LNG))
        val geofenceManager = newGeofenceManager()
        geofenceManager.addLocationId(faraway.id, LocationSetChangeCause.ENTER)

        newReconciler(geofenceManager).reconcile()

        assertEquals(setOf(here.id, nearby.id), geofenceManager.currentLocationIds.value)
    }

    @Test
    fun `overlapping locations are each judged against the same fix`() = runTest {
        // Halfway between `here` and `faraway`: inside `faraway`'s wide radius, outside the others.
        currentLocationReturns(fixAt(FIX_LAT + 0.004, FIX_LNG))
        val geofenceManager = newGeofenceManager()
        geofenceManager.addLocationId(here.id, LocationSetChangeCause.ENTER)

        newReconciler(geofenceManager).reconcile()

        assertEquals(setOf(faraway.id), geofenceManager.currentLocationIds.value)
    }

    @Test
    fun `a distance exactly equal to the radius counts as inside`() = runTest {
        val fix = fixAt(FIX_LAT, FIX_LNG)
        val exactly = LocationEntity(
            id = 9,
            name = "Edge",
            lat = FIX_LAT + 0.004,
            lng = FIX_LNG,
            radiusM = distanceBetween(fix, FIX_LAT + 0.004, FIX_LNG),
        )
        coEvery { locationRepository.getAllList() } returns listOf(exactly)
        currentLocationReturns(fix)
        val geofenceManager = newGeofenceManager()

        newReconciler(geofenceManager).reconcile()

        assertEquals(setOf(exactly.id), geofenceManager.currentLocationIds.value)
    }

    @Test
    fun `a radius below the fence minimum is judged by the minimum the fence uses`() = runTest {
        val tiny = LocationEntity(id = 8, name = "Tiny", lat = FIX_LAT + 0.0005, lng = FIX_LNG, radiusM = 40f)
        coEvery { locationRepository.getAllList() } returns listOf(tiny)
        currentLocationReturns(fixAt(FIX_LAT, FIX_LNG))
        val geofenceManager = newGeofenceManager()

        newReconciler(geofenceManager).reconcile()

        assertEquals(setOf(tiny.id), geofenceManager.currentLocationIds.value)
    }

    @Test
    fun `a failed fix leaves the recorded set untouched rather than clearing it`() = runTest {
        currentLocationFailsWith(IllegalStateException("no provider"))
        val geofenceManager = newGeofenceManager()
        geofenceManager.addLocationId(faraway.id, LocationSetChangeCause.ENTER)

        newReconciler(geofenceManager).reconcile()

        assertEquals(setOf(faraway.id), geofenceManager.currentLocationIds.value)
    }

    @Test
    fun `a fix that never arrives leaves the recorded set untouched rather than clearing it`() = runTest {
        currentLocationNeverSettles()
        val geofenceManager = newGeofenceManager()
        geofenceManager.addLocationId(faraway.id, LocationSetChangeCause.ENTER)

        newReconciler(geofenceManager).reconcile()

        assertEquals(setOf(faraway.id), geofenceManager.currentLocationIds.value)
    }

    @Test
    fun `a lastLocation older than the freshness window is rejected`() = runTest {
        currentLocationReturns(null)
        every { fusedLocationClient.lastLocation } returns
            Tasks.forResult(fixAt(FIX_LAT, FIX_LNG, ageMillis = 20 * 60 * 1000L))
        val geofenceManager = newGeofenceManager()

        newReconciler(geofenceManager).reconcile()

        assertEquals(emptySet<Long>(), geofenceManager.currentLocationIds.value)
    }

    @Test
    fun `a recent lastLocation stands in for a current fix`() = runTest {
        currentLocationReturns(null)
        every { fusedLocationClient.lastLocation } returns
            Tasks.forResult(fixAt(FIX_LAT, FIX_LNG, ageMillis = 60 * 1000L))
        val geofenceManager = newGeofenceManager()

        newReconciler(geofenceManager).reconcile()

        assertEquals(setOf(here.id, nearby.id), geofenceManager.currentLocationIds.value)
    }

    @Test
    fun `a second reconciliation inside the debounce window asks for no new fix`() = runTest {
        currentLocationReturns(fixAt(FIX_LAT, FIX_LNG))
        val reconciler = newReconciler(newGeofenceManager())

        reconciler.reconcile()
        reconciler.reconcile()

        verify(exactly = 1) { fusedLocationClient.getCurrentLocation(any<Int>(), any<CancellationToken>()) }
    }

    @Test
    fun `without fine location permission nothing is requested and nothing changes`() = runTest {
        shadowOf(context as Application).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val geofenceManager = newGeofenceManager()
        geofenceManager.addLocationId(faraway.id, LocationSetChangeCause.ENTER)

        newReconciler(geofenceManager).reconcile()

        assertEquals(setOf(faraway.id), geofenceManager.currentLocationIds.value)
        verify(exactly = 0) { fusedLocationClient.getCurrentLocation(any<Int>(), any<CancellationToken>()) }
    }

    @Test
    fun `availability answers from the recorded set while a fix is still outstanding`() = runTest {
        currentLocationNeverSettles()
        val geofenceManager = newGeofenceManager()
        geofenceManager.addLocationId(here.id, LocationSetChangeCause.ENTER)
        val triggerRepository: TriggerRepository = mockk(relaxed = true)
        coEvery { triggerRepository.countCompletedSince(any(), any()) } returns 0
        coEvery { triggerRepository.countDailyCompletionsSince(any(), any()) } returns 0
        coEvery { triggerRepository.getLastFiredOrDismissedForHabit(any()) } returns null
        val availabilityService = HabitAvailabilityService(
            mockk<HabitRepository>(relaxed = true),
            mockk<WindowRepository>(relaxed = true),
            triggerRepository,
            geofenceManager,
        )
        // Detached from the test scheduler so the stalled fix stays stalled in real time.
        CoroutineScope(Dispatchers.Unconfined).launch { newReconciler(geofenceManager).reconcile() }

        val status = availabilityService.computeAvailability(habit, setOf(here.id), emptySet())

        assertEquals(AvailabilityStatus.Available, status)
    }

    companion object {
        private const val PREFS_NAME = "geofence_prefs"

        private const val FIX_LAT = 40.0
        private const val FIX_LNG = -3.0

        private val here = LocationEntity(id = 1, name = "Here", lat = FIX_LAT, lng = FIX_LNG, radiusM = 100f)
        // ~55 m north of the fix: a second fence the same fix also sits inside.
        private val nearby = LocationEntity(id = 2, name = "Nearby", lat = FIX_LAT + 0.0005, lng = FIX_LNG, radiusM = 100f)
        // ~890 m north of the fix, with a radius wide enough to still contain a point halfway there.
        private val faraway = LocationEntity(id = 3, name = "Faraway", lat = FIX_LAT + 0.008, lng = FIX_LNG, radiusM = 600f)

        private val habit = HabitEntity(
            id = 1L,
            name = "meditation",
            descriptionLadder = listOf("3 deep breaths", "", "", "20-minute guided meditation", "", ""),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

        private fun distanceBetween(fix: Location, lat: Double, lng: Double): Float {
            val results = FloatArray(1)
            Location.distanceBetween(fix.latitude, fix.longitude, lat, lng, results)
            return results[0]
        }
    }
}
