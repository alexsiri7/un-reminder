package net.interstellarai.unreminder.service.geofence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import net.interstellarai.unreminder.data.repository.LocationRepository
import org.junit.After
import org.junit.Assert.assertEquals
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
        // Pre-seed prefs with a mix of valid and garbage data
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_LOCATION_IDS, setOf("7", "not-a-number", "42"))
            .commit()

        val mgr = GeofenceManager(context, locationRepository)
        assertEquals(setOf(7L, 42L), mgr.currentLocationIds.value)
    }

    companion object {
        // Mirror of GeofenceManager's private prefs schema; renaming there must rename here
        // or older installs silently fail to rehydrate (the bug from issue #245).
        private const val PREFS_NAME = "geofence_prefs"
        private const val KEY_LOCATION_IDS = "current_location_ids"
    }
}
