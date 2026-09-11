package net.interstellarai.unreminder.service.geofence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeofenceManagerTest {

    private lateinit var context: Context
    private val locationRepository: LocationRepository = mockk(relaxed = true)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        unmockkStatic(Sentry::class)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `currentLocationIds is empty on first construct with no prefs`() {
        val mgr = GeofenceManager(context, locationRepository)
        assertEquals(emptySet<Long>(), mgr.currentLocationIds.value)
    }

    @Test
    fun `addLocationId persists and survives reconstruction`() {
        val first = GeofenceManager(context, locationRepository)
        first.addLocationId(7L)
        first.addLocationId(42L)

        // Simulate process death by constructing a fresh instance from the same prefs
        val rehydrated = GeofenceManager(context, locationRepository)

        assertEquals(setOf(7L, 42L), rehydrated.currentLocationIds.value)
    }

    @Test
    fun `removeLocationId persists removal across reconstruction`() {
        val first = GeofenceManager(context, locationRepository)
        first.addLocationId(7L)
        first.addLocationId(42L)
        first.removeLocationId(7L)

        val rehydrated = GeofenceManager(context, locationRepository)
        assertEquals(setOf(42L), rehydrated.currentLocationIds.value)
    }

    @Test
    fun `addLocationId emits new value on the StateFlow`() {
        val mgr = GeofenceManager(context, locationRepository)
        val seen = mutableListOf<Set<Long>>()
        seen += mgr.currentLocationIds.value
        mgr.addLocationId(5L)
        seen += mgr.currentLocationIds.value
        assertEquals(listOf(emptySet(), setOf(5L)), seen)
    }

    @Test
    fun `addLocationId is idempotent for the same id`() {
        val mgr = GeofenceManager(context, locationRepository)
        mgr.addLocationId(9L)
        mgr.addLocationId(9L)
        assertEquals(setOf(9L), mgr.currentLocationIds.value)

        val rehydrated = GeofenceManager(context, locationRepository)
        assertEquals(setOf(9L), rehydrated.currentLocationIds.value)
    }

    @Test
    fun `loadPersisted ignores malformed entries instead of crashing`() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_LOCATION_IDS, setOf("7", "not-a-number", "42"))
            .commit()

        val mgr = GeofenceManager(context, locationRepository)
        assertEquals(setOf(7L, 42L), mgr.currentLocationIds.value)
    }

    @Test
    fun `registerAllFromDb raises a sub-minimum radius, persists it under the same id and registers the raised fence`() = runTest {
        mockkStatic(Sentry::class)
        every { Sentry.captureMessage(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID
        val stored = LocationEntity(id = 3, name = "Home", lat = 51.5, lng = -0.1, radiusM = 40f)
        coEvery { locationRepository.getAllList() } returns listOf(stored)
        val mgr = spyk(GeofenceManager(context, locationRepository))

        mgr.registerAllFromDb()

        coVerify(exactly = 1) { locationRepository.update(stored.copy(radiusM = 100f)) }
        verify(exactly = 1) { mgr.registerGeofence(3L, "Home", 51.5, -0.1, 100f) }
        verify(exactly = 1) { Sentry.captureMessage(any(), any<ScopeCallback>()) }
    }

    @Test
    fun `radius-raise telemetry carries only id, name and radii, never coordinates`() = runTest {
        mockkStatic(Sentry::class)
        val callback = slot<ScopeCallback>()
        every { Sentry.captureMessage(any(), capture(callback)) } returns SentryId.EMPTY_ID
        val stored = LocationEntity(id = 3, name = "Home", lat = 51.5, lng = -0.1, radiusM = 40f)
        coEvery { locationRepository.getAllList() } returns listOf(stored)
        val mgr = spyk(GeofenceManager(context, locationRepository))

        mgr.registerAllFromDb()

        val extras = mutableMapOf<String, String>()
        val scope = mockk<IScope>(relaxed = true)
        every { scope.setExtra(any(), any()) } answers { extras[firstArg()] = secondArg() }
        callback.captured.run(scope)

        assertEquals(
            mapOf(
                "location_id" to "3",
                "location_name" to "Home",
                "old_radius_m" to "40.0",
                "new_radius_m" to "100.0"
            ),
            extras
        )
        verify(exactly = 1) { scope.setTag("component", "geofence") }
        val payload = extras.values.joinToString()
        assertFalse(payload.contains("51.5"))
        assertFalse(payload.contains("-0.1"))
    }

    @Test
    fun `registerAllFromDb leaves a location at exactly the minimum radius untouched`() = runTest {
        mockkStatic(Sentry::class)
        every { Sentry.captureMessage(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID
        val stored = LocationEntity(id = 4, name = "Gym", lat = 48.8, lng = 2.3, radiusM = 100f)
        coEvery { locationRepository.getAllList() } returns listOf(stored)
        val mgr = spyk(GeofenceManager(context, locationRepository))

        mgr.registerAllFromDb()

        coVerify(exactly = 0) { locationRepository.update(any()) }
        verify(exactly = 1) { mgr.registerGeofence(4L, "Gym", 48.8, 2.3, 100f) }
        verify(exactly = 0) { Sentry.captureMessage(any(), any<ScopeCallback>()) }
    }

    companion object {
        // Mirror of GeofenceManager's private prefs schema; renaming there must rename here
        // or older installs silently lose persisted state on rehydration.
        private const val PREFS_NAME = "geofence_prefs"
        private const val KEY_LOCATION_IDS = "current_location_ids"
    }
}
