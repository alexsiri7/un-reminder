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
import com.google.android.gms.tasks.TaskCompletionSource
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.data.db.LocationEntity
import net.interstellarai.unreminder.data.repository.LocationRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant

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
        every { Sentry.captureException(any()) } returns SentryId.EMPTY_ID

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

    // Refresh tests pass the runTest scope so launched work runs on the test dispatcher's
    // virtual time; nothing else launches on the scope.
    private fun newManager(scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)) =
        GeofenceManager(context, locationRepository, geofencingClient, settingsClient, scope)

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

    private fun stallRegistrationOf(requestId: String) {
        val neverSettled = TaskCompletionSource<Void>()
        every {
            geofencingClient.addGeofences(
                match<GeofencingRequest> { it.geofences.single().requestId == requestId },
                any<PendingIntent>()
            )
        } returns neverSettled.task
    }

    private fun stallLocationSettingsCheck() {
        val neverSettled = TaskCompletionSource<LocationSettingsResponse>()
        every { settingsClient.checkLocationSettings(any()) } returns neverSettled.task
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

    private fun registrationFailures(): List<CapturedScope> =
        captured.filter { (message, _) -> message == "Geofence registration failed" }.map { it.second.record() }

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
    fun `recordReconciliation swaps the whole set in one persisted step`() {
        val mgr = newManager()
        mgr.addLocationId(7L, LocationSetChangeCause.ENTER)
        breadcrumbs.clear()

        mgr.recordReconciliation(mapOf(7L to false, 42L to true, 99L to true))

        assertEquals(setOf(42L, 99L), mgr.currentLocationIds.value)
        assertEquals(setOf(42L, 99L), newManager().currentLocationIds.value)
        assertEquals(
            listOf(Triple("reconcile", "[7]", "[42, 99]")),
            breadcrumbs.filter { it.getData("cause") == "reconcile" }.map { it.causeAndSets() }
        )
    }

    @Test
    fun `recordReconciliation on the set already held leaves no breadcrumb but still refreshes the checks`() {
        val mgr = newManager()
        mgr.addLocationId(7L, LocationSetChangeCause.ENTER)
        breadcrumbs.clear()

        mgr.recordReconciliation(mapOf(7L to true))

        assertEquals(setOf(7L), mgr.currentLocationIds.value)
        assertTrue(breadcrumbs.isEmpty())
        assertEquals(LocationSetChangeCause.RECONCILE, mgr.locationChecks.value.getValue(7L).via)
    }

    @Test
    fun `recordReconciliation stamps every evaluated location and drops the ones it no longer covers`() {
        val mgr = newManager()
        mgr.addLocationId(7L, LocationSetChangeCause.ENTER)

        mgr.recordReconciliation(mapOf(42L to true, 99L to false))

        assertEquals(setOf(42L, 99L), mgr.locationChecks.value.keys)
        assertTrue(mgr.locationChecks.value.getValue(42L).inside)
        assertFalse(mgr.locationChecks.value.getValue(99L).inside)
    }

    @Test
    fun `an arrival stamps only the location it names`() {
        val mgr = newManager()
        mgr.addLocationId(7L, LocationSetChangeCause.ENTER)

        assertEquals(setOf(7L), mgr.locationChecks.value.keys)
        assertTrue(mgr.locationChecks.value.getValue(7L).inside)
        assertEquals(LocationSetChangeCause.ENTER, mgr.locationChecks.value.getValue(7L).via)
    }

    @Test
    fun `a departure for a location already believed outside still records that answer`() {
        val mgr = newManager()

        mgr.removeLocationId(7L, LocationSetChangeCause.EXIT)

        assertEquals(emptySet<Long>(), mgr.currentLocationIds.value)
        assertFalse(mgr.locationChecks.value.getValue(7L).inside)
        assertEquals(LocationSetChangeCause.EXIT, mgr.locationChecks.value.getValue(7L).via)
    }

    @Test
    fun `checks survive reconstruction with their original times and causes`() {
        val first = newManager()
        first.addLocationId(7L, LocationSetChangeCause.ENTER)
        first.removeLocationId(42L, LocationSetChangeCause.EXIT)

        val rehydrated = newManager()

        assertEquals(first.locationChecks.value, rehydrated.locationChecks.value)
        assertEquals(LocationSetChangeCause.ENTER, rehydrated.locationChecks.value.getValue(7L).via)
        assertEquals(LocationSetChangeCause.EXIT, rehydrated.locationChecks.value.getValue(42L).via)
    }

    @Test
    fun `upgrading from prefs that hold only the legacy id set yields no checks`() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_LOCATION_IDS, setOf("7"))
            .commit()

        val mgr = newManager()

        assertEquals(setOf(7L), mgr.currentLocationIds.value)
        assertEquals(emptyMap<Long, LocationCheck>(), mgr.locationChecks.value)
    }

    @Test
    fun `on a manager with no legacy state, inside checks and the id set stay identical`() {
        val mgr = newManager()

        mgr.addLocationId(7L, LocationSetChangeCause.ENTER)
        assertEquals(mgr.currentLocationIds.value, mgr.locationChecks.value.filterValues { it.inside }.keys)

        mgr.removeLocationId(7L, LocationSetChangeCause.EXIT)
        assertEquals(mgr.currentLocationIds.value, mgr.locationChecks.value.filterValues { it.inside }.keys)

        mgr.recordReconciliation(mapOf(7L to false, 42L to true))
        assertEquals(mgr.currentLocationIds.value, mgr.locationChecks.value.filterValues { it.inside }.keys)
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
    fun `malformed persisted checks are dropped instead of crashing`() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_LOCATION_IDS, setOf("7"))
            .putStringSet(
                KEY_LOCATION_CHECKS,
                setOf("7|1|1757580000000|ENTER", "42|1|ENTER", "43|2|1757580000000|ENTER", "44|1|now|EXIT", "45|0|1757580000000|SOMEDAY"),
            )
            .commit()

        val mgr = newManager()

        assertEquals(setOf(7L), mgr.locationChecks.value.keys)
        assertEquals(
            LocationCheck(inside = true, at = Instant.ofEpochMilli(1757580000000L), via = LocationSetChangeCause.ENTER),
            mgr.locationChecks.value.getValue(7L),
        )
    }

    @Test
    fun `every currentLocationIds mutation leaves a breadcrumb naming its cause`() = runTest {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_LOCATION_IDS, setOf("7"))
            .commit()

        val mgr = newManager()
        mgr.addLocationId(42L, LocationSetChangeCause.ENTER)
        mgr.addLocationId(42L, LocationSetChangeCause.ENTER)
        mgr.removeLocationId(7L, LocationSetChangeCause.EXIT)
        mgr.removeLocationId(7L, LocationSetChangeCause.EXIT)
        mgr.removeGeofence(42L, "Home")

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
    fun `registrationHealth is unknown until the first full registration completes`() = runTest {
        coEvery { locationRepository.getAllList() } returns emptyList()
        val mgr = newManager()

        assertNull(mgr.registrationHealth.value)
        mgr.registerAllFromDb()

        val health = mgr.registrationHealth.value
        assertNotNull(health)
        assertEquals(0, health!!.savedCount)
        assertEquals(0, health.registeredCount)
        assertNull(health.lastFailure)
        assertEquals(LocationSettingsCheck.Available, health.locationSettings)
    }

    @Test
    fun `registrationHealth carries the same counts, last failure and checks the summary reports`() = runTest {
        grantLocationPermissions()
        coEvery { locationRepository.getAllList() } returns listOf(
            LocationEntity(id = 1, name = "Home", lat = 51.5, lng = -0.1, radiusM = 150f),
            LocationEntity(id = 2, name = "Gym", lat = 48.8, lng = 2.3, radiusM = 150f),
            LocationEntity(id = 3, name = "Work", lat = 40.7, lng = -74.0, radiusM = 150f),
        )
        rejectRegistrationOf("2", GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES)
        rejectRegistrationOf("3", GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE)
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        every { settingsClient.checkLocationSettings(any()) } returns
            Tasks.forException(ApiException(Status(LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE)))
        val mgr = newManager()

        mgr.registerAllFromDb()

        val health = mgr.registrationHealth.value!!
        assertEquals(3, health.savedCount)
        assertEquals(1, health.registeredCount)
        assertEquals(GeofenceRegistration.Rejected(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE), health.lastFailure)
        assertTrue(health.fineLocationGranted)
        assertTrue(health.backgroundLocationGranted)
        assertEquals(true, health.locationEnabled)
        assertEquals(LocationSettingsCheck.Unavailable(LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE), health.locationSettings)
        val summary = registrationSummary()
        assertEquals(health.savedCount.toString(), summary.extras["saved_count"])
        assertEquals(health.registeredCount.toString(), summary.extras["registered_count"])
        assertEquals(health.locationSettings.statusLabel, summary.extras["location_settings"])
    }

    @Test
    fun `registrationHealth records every location as Registered when addGeofences accepts them all`() = runTest {
        grantLocationPermissions()
        coEvery { locationRepository.getAllList() } returns listOf(
            LocationEntity(id = 1, name = "Home", lat = 51.5, lng = -0.1, radiusM = 150f),
            LocationEntity(id = 2, name = "Gym", lat = 48.8, lng = 2.3, radiusM = 150f),
        )
        val mgr = newManager()

        mgr.registerAllFromDb()

        val health = mgr.registrationHealth.value!!
        assertEquals(
            listOf(1L to GeofenceRegistration.Registered, 2L to GeofenceRegistration.Registered),
            health.outcomes
        )
        assertEquals(2, health.registeredCount)
        assertNull(health.lastFailure)
        assertEquals("2", registrationSummary().extras["registered_count"])
        assertEquals("", registrationSummary().extras["failures"])
        verify(exactly = 2) { geofencingClient.addGeofences(any<GeofencingRequest>(), any<PendingIntent>()) }
    }

    @Test
    fun `registrationHealth records missing permissions and when it was checked`() = runTest {
        coEvery { locationRepository.getAllList() } returns listOf(
            LocationEntity(id = 1, name = "Home", lat = 51.5, lng = -0.1, radiusM = 150f),
        )
        val before = java.time.Instant.now()
        val mgr = newManager()

        mgr.registerAllFromDb()

        val health = mgr.registrationHealth.value!!
        assertEquals(GeofenceRegistration.PermissionMissing, health.lastFailure)
        assertFalse(health.backgroundLocationGranted)
        assertFalse(health.checkedAt.isBefore(before))
    }

    @Test
    fun `a single registration rejected by the platform is reported to Sentry`() = runTest {
        grantLocationPermissions()
        rejectRegistrationOf("5", GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE)

        val outcome = newManager().registerGeofence(5L, "Cafe", 51.5, -0.1, 150f)

        assertEquals(GeofenceRegistration.Rejected(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE), outcome)
        val failure = registrationFailures().single()
        assertEquals("geofence", failure.tags["component"])
        assertEquals("5", failure.extras["location_id"])
        assertEquals("Cafe", failure.extras["location_name"])
        assertEquals("GEOFENCE_NOT_AVAILABLE(1000)", failure.extras["status"])
        assertTrue(captured.none { (message, _) -> message == "Geofence registration summary" })
    }

    @Test
    fun `a single registration without permissions is reported to Sentry`() = runTest {
        val outcome = newManager().registerGeofence(5L, "Cafe", 51.5, -0.1, 150f)

        assertEquals(GeofenceRegistration.PermissionMissing, outcome)
        assertEquals("PERMISSION_MISSING", registrationFailures().single().extras["status"])
    }

    @Test
    fun `a successful single registration is not reported as a failure`() = runTest {
        grantLocationPermissions()

        val outcome = newManager().registerGeofence(5L, "Cafe", 51.5, -0.1, 150f)

        assertEquals(GeofenceRegistration.Registered, outcome)
        assertTrue(captured.isEmpty())
    }

    @Test
    fun `an addGeofences task that never settles is reported as a timeout instead of hanging`() = runTest {
        grantLocationPermissions()
        coEvery { locationRepository.getAllList() } returns listOf(
            LocationEntity(id = 1, name = "Home", lat = 51.5, lng = -0.1, radiusM = 150f),
        )
        stallRegistrationOf("1")

        newManager().registerAllFromDb()

        assertEquals("id=1 status=TIMEOUT", registrationSummary().extras["failures"])
        assertEquals("TIMEOUT", registrationFailures().single().extras["status"])
    }

    @Test
    fun `removeGeofence keeps the id until the platform removal settles`() = runTest {
        val pending = TaskCompletionSource<Void>()
        every { geofencingClient.removeGeofences(any<List<String>>()) } returns pending.task
        val mgr = newManager()
        mgr.addLocationId(42L, LocationSetChangeCause.ENTER)

        val job = launch { mgr.removeGeofence(42L, "Home") }
        runCurrent()

        assertEquals(setOf(42L), mgr.currentLocationIds.value)

        pending.setResult(null)
        job.join()

        assertEquals(emptySet<Long>(), mgr.currentLocationIds.value)
        assertTrue(captured.isEmpty())
    }

    @Test
    fun `a rejected removeGeofences is reported and still drops the id`() = runTest {
        every { geofencingClient.removeGeofences(any<List<String>>()) } returns
            Tasks.forException(ApiException(Status(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE)))
        val mgr = newManager()
        mgr.addLocationId(42L, LocationSetChangeCause.ENTER)

        mgr.removeGeofence(42L, "Home")

        val failure = captured.single { (message, _) -> message == "Geofence removal failed" }.second.record()
        assertEquals("geofence", failure.tags["component"])
        assertEquals("42", failure.extras["location_id"])
        assertEquals("Home", failure.extras["location_name"])
        assertEquals("GEOFENCE_NOT_AVAILABLE(1000)", failure.extras["status"])
        assertEquals(emptySet<Long>(), mgr.currentLocationIds.value)
    }

    @Test
    fun `a removeGeofences task that never settles is reported as a timeout instead of hanging`() = runTest {
        val neverSettled = TaskCompletionSource<Void>()
        every { geofencingClient.removeGeofences(any<List<String>>()) } returns neverSettled.task
        val mgr = newManager()
        mgr.addLocationId(42L, LocationSetChangeCause.ENTER)

        mgr.removeGeofence(42L, "Home")

        val failure = captured.single { (message, _) -> message == "Geofence removal failed" }.second.record()
        assertEquals("TIMEOUT", failure.extras["status"])
        assertEquals(emptySet<Long>(), mgr.currentLocationIds.value)
    }

    @Test
    fun `cancelling a removal mid-flight ends it cancelled without reporting a failure or dropping the id`() = runTest {
        val neverSettled = TaskCompletionSource<Void>()
        every { geofencingClient.removeGeofences(any<List<String>>()) } returns neverSettled.task
        val mgr = newManager()
        mgr.addLocationId(42L, LocationSetChangeCause.ENTER)

        val job = launch { mgr.removeGeofence(42L, "Home") }
        runCurrent()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(captured.isEmpty())
        assertEquals(setOf(42L), mgr.currentLocationIds.value)
    }

    @Test
    fun `a settings check that never settles is reported as a timeout instead of hanging`() = runTest {
        coEvery { locationRepository.getAllList() } returns emptyList()
        stallLocationSettingsCheck()

        newManager().registerAllFromDb()

        assertEquals("TIMEOUT", registrationSummary().extras["location_settings"])
    }

    @Test
    fun `cancelling a registration mid-flight ends it cancelled rather than as a reported failure`() = runTest {
        grantLocationPermissions()
        stallRegistrationOf("1")
        val mgr = newManager()

        val job = launch { mgr.registerGeofence(1L, "Home", 51.5, -0.1, 150f) }
        runCurrent()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(captured.isEmpty())
    }

    @Test
    fun `cancelling the settings check mid-flight ends it cancelled without a summary`() = runTest {
        coEvery { locationRepository.getAllList() } returns emptyList()
        stallLocationSettingsCheck()
        val mgr = newManager()

        val job = launch { mgr.registerAllFromDb() }
        runCurrent()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(captured.none { (message, _) -> message == "Geofence registration summary" })
        assertNull(mgr.registrationHealth.value)
    }

    @Test
    fun `refreshRegistration updates registrationHealth without emitting a registration summary`() = runTest {
        grantLocationPermissions()
        coEvery { locationRepository.getAllList() } returns listOf(
            LocationEntity(id = 1, name = "Home", lat = 51.5, lng = -0.1, radiusM = 150f),
        )
        val mgr = newManager(this)

        mgr.refreshRegistration()
        advanceUntilIdle()

        val health = mgr.registrationHealth.value!!
        assertEquals(1, health.registeredCount)
        assertNull(health.lastFailure)
        assertTrue(captured.none { (message, _) -> message == "Geofence registration summary" })
    }

    @Test
    fun `a refresh launched after a stalled one still writes the freshest health, and a third request during the run is folded into it`() = runTest {
        coEvery { locationRepository.getAllList() } returns listOf(
            LocationEntity(id = 1, name = "Home", lat = 51.5, lng = -0.1, radiusM = 150f),
        )
        val neverSettled = TaskCompletionSource<LocationSettingsResponse>()
        every { settingsClient.checkLocationSettings(any()) } returnsMany listOf(
            neverSettled.task,
            Tasks.forResult(mockk<LocationSettingsResponse>()),
        )
        val mgr = newManager(this)

        mgr.refreshRegistration()
        runCurrent()
        grantLocationPermissions()
        mgr.refreshRegistration()
        mgr.refreshRegistration()
        advanceUntilIdle()

        val health = mgr.registrationHealth.value!!
        assertNull(health.lastFailure)
        assertEquals(1, health.registeredCount)
        assertTrue(health.backgroundLocationGranted)
        coVerify(exactly = 2) { locationRepository.getAllList() }
    }

    @Test
    fun `refresh requests made before the first one starts collapse into a single run`() = runTest {
        grantLocationPermissions()
        coEvery { locationRepository.getAllList() } returns listOf(
            LocationEntity(id = 1, name = "Home", lat = 51.5, lng = -0.1, radiusM = 150f),
        )
        val mgr = newManager(this)

        mgr.refreshRegistration()
        mgr.refreshRegistration()
        mgr.refreshRegistration()
        advanceUntilIdle()

        coVerify(exactly = 1) { locationRepository.getAllList() }
        assertEquals(1, mgr.registrationHealth.value!!.registeredCount)
    }

    @Test
    fun `refreshRegistration reports a repository failure instead of crashing the scope`() = runTest {
        coEvery { locationRepository.getAllList() } throws RuntimeException("db down")
        val mgr = newManager(this)

        mgr.refreshRegistration()
        advanceUntilIdle()

        verify(exactly = 1) { Sentry.captureException(any()) }
        assertNull(mgr.registrationHealth.value)

        mgr.refreshRegistration()
        advanceUntilIdle()

        coVerify(exactly = 2) { locationRepository.getAllList() }
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
        mgr.removeGeofence(1L, "Home")

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
        private const val KEY_LOCATION_CHECKS = "location_checks"
    }
}
