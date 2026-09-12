package net.interstellarai.unreminder.service.activity

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
class ActivityTransitionPendingIntentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Play Services writes the transition result onto this intent when it fires, so an
    // immutable one arrives with nothing to extract and no observation is ever recorded
    // (the geofence path shipped the same bug once, #339).
    @Test
    fun `the activity transition pending intent is mutable`() {
        val flags = shadowOf(ActivityTransitionReceiver.getPendingIntent(context)).flags

        assertNotEquals(0, flags and PendingIntent.FLAG_MUTABLE)
        assertEquals(0, flags and PendingIntent.FLAG_IMMUTABLE)
    }
}
