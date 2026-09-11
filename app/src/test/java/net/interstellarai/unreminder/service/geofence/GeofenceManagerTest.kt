package net.interstellarai.unreminder.service.geofence

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Tasks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.LocationRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GeofenceManagerTest {

    private lateinit var context: Context
    private val locationRepository: LocationRepository = mockk(relaxed = true)
    private val geofencingClient: GeofencingClient = mockk()
    private val settingsClient: SettingsClient = mockk()

    private val captured = mutableListOf<Pair<String, ScopeCallback>>()
    private val breadcrumbs = mutableListOf<Breadcrumb>()

    private class CapturedScope {
        val tags = mutableMapOf<String, String>()
        val extras = mutableMapOf<String, String>()
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

        mockkStatic(Sentry::class)
        every { Sentry.captureMessage(any(), any<ScopeCallback>()) } answers {
            captured += firstArg<String>() to secondArg()
            SentryId.EMPTY_ID
        }
        every { Sentry.addBreadcrumb(capture(breadcrumbs)) } just runs

        every { geofencingClient.addGeofences(any<GeofencingRequest>(), any<PendingIntent>()) } returns Tasks.forResult(null)
        every { geofencingClient.removeGeofences(any<List<String>>()) } returns Tasks.forResult(null)
        every { settingsClient.checkLocationSettings(any()) } returns Tasks.forResult(mockk<LocationSettingsResponse>())
    }

    @After
    fun tearDown() {
        unmockkStatic(Sentry::class)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun newManager() = GeofenceManager(context, locationRepository, geofencingClient, settingsClient)

    private fun grantLocationPermissions() {
        shadowOf(context as Application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )
    }

    private fun rejectRegistrationOf(requestId: String, statusCode: Int) {
        every {
            geofencingClient.addGeofences(
                match<GeofencingRequest> { it.geofences.single().requestId == requestId },
                any<PendingIntent>()
            )
        } returns Tasks.forException(ApiException(Status(statusCode)))
    }

    // Stubbing the scope inside the captureMessage `answers` block makes mockk re-run that answer
    // intermittently, so callbacks are captured raw and replayed against a recording scope here.
    private fun ScopeCallback.record(): CapturedScope {
        val recorded = CapturedScope()
        val scope = mockk<IScope>(relaxed = true)
        every { scope.setTag(any(), any()) } answers { recorded.tags[firstArg()] = secondArg() }
        every { scope.setExtra(any(), any()) } answers { recorded.extras[firstArg()] = secondArg() }
        run(scope)
        return recorded
    }

    private fun messages(): List<Pair<String, CapturedScope>> =
        captured.map { (message, callback) -> message to callback.record() }

    private fun registrationSummary(): CapturedScope =
        captured.single { (message, _) -> message == "Geofence registration summary" }.second.record()

    private fun Breadcrumb.causeAndSets(): Triple<String?, String?, String?> =
        Triple(getData("cause") as String?, getData("old_ids") as String?, getData("new_ids") as String?)

    @Test
    fun `currentLocationIds is empty on first construct with no prefs`() {
        val mgr = newManager()
        assertEquals(emptySet<Long>(), mgr.currentLocationIds.value)
    }

    @Test
    fun `addLocationId persists and survives reconstruction`() {
        val first = newManager()
        first.addLocationId(7L, LocationSetChangeCause.ENTER)
        first.addLocationId(42L, LocationSetChangeCause.ENTER)

        // Simulate process death by constructing a fresh instance from the same prefs
        val rehydrated = newManager()

        assertEquals(setOf(7L, 42L), rehydrated.currentLocationIds.value)
    }

    @Test
    fun `removeLocationId persists removal across reconstruction`() {
        val first = newManager()
        first.addLocationId(7L, LocationSetChangeCause.ENTER)
        first.addLocationId(42L, LocationSetChangeCause.ENTER)
        first.removeLocationId(7L, LocationSetChangeCause.EXIT)

        val rehydrated = newManager()
        assertEquals(setOf(42L), rehydrated.currentLocationIds.value)
    }

    @Test
    fun `addLocationId emits new value on the StateFlow`() {
        val mgr = newManager()
        val seen = mutableListOf<Set<Long>>()
        seen += mgr.currentLocationIds.value
        mgr.addLocationId(5L, LocationSetChangeCause.ENTER)
        seen += mgr.currentLocationIds.value
        assertEquals(listOf(emptySet(), setOf(5L)), seen)
    }

    @Test
    fun `addLocationId is idempotent for the same id`() {
        val mgr = newManager()
        mgr.addLocationId(9L, LocationSetChangeCause.ENTER)
        mgr.addLocationId(9L, LocationSetChangeCause.ENTER)
        assertEquals(setOf(9L), mgr.currentLocationIds.value)

        val rehydrated = newManager()
        assertEquals(setOf(9L), rehydrated.currentLocationIds.value)
    }

    @Test
    fun `loadPersisted ignores malformed entries instead of crashing`() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_LOCATION_IDS, setOf("7", "not-a-number", "42"))
            .commit()

        val mgr = newManager()
        assertEquals(setOf(7L, 42L), mgr.currentLocationIds.value)
    }

    @Test
    fun `every currentLocationIds mutation leaves a breadcrumb naming its cause`() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_LOCATION_IDS, setOf("7"))
            .commit()

        val mgr = newManager()
        mgr.addLocationId(42L, LocationSetChangeCause.ENTER)
        mgr.addLocationId(42L, LocationSetChangeCause.ENTER)
        mgr.removeLocationId(7L, LocationSetChangeCause.EXIT)
        mgr.removeLocationId(7L, LocationSetChangeCause.EXIT)
        mgr.removeGeofence(42L)

        assertEquals(
            listOf(
                Triple("restore", "[]", "[7]"),
                Triple("enter", "[7]", "[7, 42]"),
                Triple("exit", "[7, 42]", "[42]"),
                Triple("manual", "[42]", "[]"),
            ),
            breadcrumbs.map { it.causeAndSets() }
        )
        assertTrue(breadcrumbs.all { it.category == "geofence" })
    }

    @Test
    fun `registerAllFromDb reports saved, registered and failed counts with failure status codes`() = runTest {
        grantLocationPermissions()
        coEvery { locationRepository.getAllList() } returns listOf(
            LocationEntity(id = 1, name = "Home", lat = 51.5, lng = -0.1, radiusM = 150f),
            LocationEntity(id = 2, name = "Gym", lat = 48.8, lng = 2.3, radiusM = 150f),
            LocationEntity(id = 3, name = "Work", lat = 40.7, lng = -74.0, radiusM = 150f),
        )
        rejectRegistrationOf("2", GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE)
        rejectRegistrationOf("3", GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES)

        newManager().registerAllFromDb()

        val summary = registrationSummary()
        assertEquals("geofence", summary.tags["component"])
        assertEquals("3", summary.extras["saved_count"])
        assertEquals("1", summary.extras["registered_count"])
        assertEquals("2", summary.extras["failed_count"])
        assertEquals(
            "id=2 status=GEOFENCE_NOT_AVAILABLE(1000), id=3 status=GEOFENCE_TOO_MANY_GEOFENCES(1001)",
            summary.extras["failures"]
        )
        assertEquals("true", summary.extras["fine_location_granted"])
        assertEquals("true", summary.extras["background_location_granted"])
    }

    @Test
    fun `missing permissions are reported in the registration summary rather than only logged`() = runTest {
        coEvery { locationRepository.getAllList() } returns listOf(
            LocationEntity(id = 1, name = "Home", lat = 51.5, lng = -0.1, radiusM = 150f),
        )

        newManager().registerAllFromDb()

        val summary = registrationSummary()
        assertEquals("0", summary.extras["registered_count"])
        assertEquals("1", summary.extras["failed_count"])
        assertEquals("id=1 status=PERMISSION_MISSING", summary.extras["failures"])
        assertEquals("false", summary.extras["fine_location_granted"])
        assertEquals("false", summary.extras["background_location_granted"])
        verify(exactly = 0) { geofencingClient.addGeofences(any<GeofencingRequest>(), any<PendingIntent>()) }
    }

    @Test
    fun `registration summary carries system location state and the settings check result`() = runTest {
        coEvery { locationRepository.getAllList() } returns emptyList()
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(false)
        every { settingsClient.checkLocationSettings(any()) } returns
            Tasks.forException(ApiException(Status(LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE)))

        newManager().registerAllFromDb()

        val summary = registrationSummary()
        assertEquals("0", summary.extras["saved_count"])
        assertEquals("false", summary.extras["location_enabled"])
        assertEquals("SETTINGS_CHANGE_UNAVAILABLE(8502)", summary.extras["location_settings"])
    }

    @Test
    fun `registration summary reports available location settings as success`() = runTest {
        coEvery { locationRepository.getAllList() } returns emptyList()
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(true)

        newManager().registerAllFromDb()

        val summary = registrationSummary()
        assertEquals("true", summary.extras["location_enabled"])
        assertEquals("SUCCESS(0)", summary.extras["location_settings"])
    }

    @Test
    fun `no geofence telemetry carries coordinates`() = runTest {
        grantLocationPermissions()
        coEvery { locationRepository.getAllList() } returns listOf(
            LocationEntity(id = 1, name = "Home", lat = 51.5074, lng = -0.1278, radiusM = 40f),
            LocationEntity(id = 2, name = "Gym", lat = 48.8566, lng = 2.3522, radiusM = 150f),
        )
        rejectRegistrationOf("2", GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE)
        every { settingsClient.checkLocationSettings(any()) } returns
            Tasks.forException(ApiException(Status(LocationSettingsStatusCodes.RESOLUTION_REQUIRED)))

        val mgr = newManager()
        mgr.registerAllFromDb()
        mgr.addLocationId(1L, LocationSetChangeCause.ENTER)
        mgr.removeGeofence(1L)

        val payloadStrings = messages().flatMap { (message, scope) ->
            listOf(message) + scope.tags.entries.map { "${it.key}=${it.value}" } +
                scope.extras.entries.map { "${it.key}=${it.value}" }
        } + breadcrumbs.flatMap { crumb ->
            listOfNotNull(crumb.message, crumb.category) + crumb.data.entries.map { "${it.key}=${it.value}" }
        }
        assertTrue(payloadStrings.size > 10)
        for (coordinate in listOf("51.5074", "0.1278", "48.8566", "2.3522")) {
            val leaked = payloadStrings.filter { it.contains(coordinate) }
            assertTrue("coordinate $coordinate leaked in $leaked", leaked.isEmpty())
        }
    }

    @Test
    fun `registerAllFromDb raises a sub-minimum radius, persists it under the same id and registers the raised fence`() = runTest {
        val stored = LocationEntity(id = 3, name = "Home", lat = 51.5, lng = -0.1, radiusM = 40f)
        coEvery { locationRepository.getAllList() } returns listOf(stored)
        val mgr = spyk(newManager())

        mgr.registerAllFromDb()

        coVerify(exactly = 1) { locationRepository.update(stored.copy(radiusM = 100f)) }
        coVerify(exactly = 1) { mgr.registerGeofence(3L, "Home", 51.5, -0.1, 100f) }
        assertEquals(1, captured.count { (message, _) -> message == "Geofence radius raised to minimum" })
    }

    @Test
    fun `radius-raise telemetry carries only id, name and radii, never coordinates`() = runTest {
        val stored = LocationEntity(id = 3, name = "Home", lat = 51.5, lng = -0.1, radiusM = 40f)
        coEvery { locationRepository.getAllList() } returns listOf(stored)

        newManager().registerAllFromDb()

        val raised = captured.single { (message, _) -> message == "Geofence radius raised to minimum" }.second.record()
        assertEquals(
            mapOf(
                "location_id" to "3",
                "location_name" to "Home",
                "old_radius_m" to "40.0",
                "new_radius_m" to "100.0"
            ),
            raised.extras
        )
        assertEquals("geofence", raised.tags["component"])
    }

    @Test
    fun `registerAllFromDb leaves a location at exactly the minimum radius untouched`() = runTest {
        val stored = LocationEntity(id = 4, name = "Gym", lat = 48.8, lng = 2.3, radiusM = 100f)
        coEvery { locationRepository.getAllList() } returns listOf(stored)
        val mgr = spyk(newManager())

        mgr.registerAllFromDb()

        coVerify(exactly = 0) { locationRepository.update(any()) }
        coVerify(exactly = 1) { mgr.registerGeofence(4L, "Gym", 48.8, 2.3, 100f) }
        assertFalse(captured.any { (message, _) -> message == "Geofence radius raised to minimum" })
    }

    companion object {
        // Mirror of GeofenceManager's private prefs schema; renaming there must rename here
        // or older installs silently lose persisted state on rehydration.
        private const val PREFS_NAME = "geofence_prefs"
        private const val KEY_LOCATION_IDS = "current_location_ids"
    }
}
