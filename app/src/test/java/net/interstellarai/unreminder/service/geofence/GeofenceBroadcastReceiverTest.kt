package net.interstellarai.unreminder.service.geofence

import android.app.Application
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.internal.GeneratedComponentManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.interstellarai.unreminder.widget.WidgetRefresher
import org.junit.After
import org.junit.Before
import org.junit.Test

class GeofenceBroadcastReceiverTest {

    private val context: Context = mockk(relaxed = true)
    private val intent: Intent = mockk(relaxed = true)
    private val event: GeofencingEvent = mockk()
    private val geofenceManager: GeofenceManager = mockk(relaxUnitFun = true)
    private val widgetRefresher: WidgetRefresher = mockk(relaxUnitFun = true)

    private val receiver = GeofenceBroadcastReceiver().apply {
        geofenceManager = this@GeofenceBroadcastReceiverTest.geofenceManager
        widgetRefresher = this@GeofenceBroadcastReceiverTest.widgetRefresher
    }

    @Before
    fun setup() {
        // The Hilt base class injects from the application on every onReceive; the fields
        // are set by hand above, so the injector only has to exist.
        val injector: GeofenceBroadcastReceiver_GeneratedInjector = mockk(relaxUnitFun = true)
        val application = mockk<Application>(moreInterfaces = arrayOf(GeneratedComponentManager::class))
        every { (application as GeneratedComponentManager<*>).generatedComponent() } returns injector
        every { context.applicationContext } returns application

        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(intent) } returns event
        every { event.hasError() } returns false
    }

    @After
    fun tearDown() {
        unmockkStatic(GeofencingEvent::class)
    }

    private fun givenTransition(transition: Int, vararg requestIds: String) {
        every { event.geofenceTransition } returns transition
        every { event.triggeringGeofences } returns requestIds.map { id ->
            mockk<Geofence> { every { requestId } returns id }
        }
    }

    @Test
    fun `entering a geofence records the location and refreshes the widget`() {
        givenTransition(Geofence.GEOFENCE_TRANSITION_ENTER, "5")

        receiver.onReceive(context, intent)

        verify(exactly = 1) { geofenceManager.addLocationId(5L) }
        verify(exactly = 1) { widgetRefresher.refresh() }
    }

    @Test
    fun `leaving a geofence refreshes the widget once for the whole batch`() {
        givenTransition(Geofence.GEOFENCE_TRANSITION_EXIT, "5", "6")

        receiver.onReceive(context, intent)

        verify(exactly = 1) { geofenceManager.removeLocationId(5L) }
        verify(exactly = 1) { geofenceManager.removeLocationId(6L) }
        verify(exactly = 1) { widgetRefresher.refresh() }
    }

    @Test
    fun `a transition with no usable geofence leaves the widget alone`() {
        givenTransition(Geofence.GEOFENCE_TRANSITION_ENTER, "not-an-id")

        receiver.onReceive(context, intent)

        verify(exactly = 0) { widgetRefresher.refresh() }
    }
}
