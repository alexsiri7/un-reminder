package net.interstellarai.unreminder.service.geofence

import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GeofencePendingIntentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Play Services writes the transition extras onto this intent when a fence fires, so an
    // immutable one makes every addGeofences call fail with DEVELOPER_ERROR (see #339).
    @Test
    fun `the geofence pending intent is mutable`() {
        val flags = shadowOf(GeofenceBroadcastReceiver.getPendingIntent(context)).flags

        assertNotEquals(0, flags and PendingIntent.FLAG_MUTABLE)
        assertEquals(0, flags and PendingIntent.FLAG_IMMUTABLE)
    }
}
