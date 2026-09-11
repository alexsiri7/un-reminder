package net.interstellarai.unreminder.service.geofence

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LocationSettingsChangedReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val geofenceManager: GeofenceManager = mockk(relaxUnitFun = true)
    private val receiver = LocationSettingsChangedReceiver(geofenceManager)

    @Test
    fun `MODE_CHANGED refreshes registration`() {
        receiver.onReceive(context, Intent(LocationManager.MODE_CHANGED_ACTION))

        verify(exactly = 1) { geofenceManager.refreshRegistration() }
    }

    @Test
    fun `PROVIDERS_CHANGED refreshes registration`() {
        receiver.onReceive(context, Intent(LocationManager.PROVIDERS_CHANGED_ACTION))

        verify(exactly = 1) { geofenceManager.refreshRegistration() }
    }

    @Test
    fun `an unrelated action is ignored`() {
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        verify(exactly = 0) { geofenceManager.refreshRegistration() }
    }

    @Test
    fun `register wires the filter so a system broadcast reaches the manager`() {
        LocationSettingsChangedReceiver.register(context, geofenceManager)

        context.sendBroadcast(Intent(LocationManager.MODE_CHANGED_ACTION))
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { geofenceManager.refreshRegistration() }
    }
}
