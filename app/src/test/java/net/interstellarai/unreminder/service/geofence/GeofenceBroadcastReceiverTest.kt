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
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.IScope
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import kotlinx.coroutines.flow.MutableStateFlow
import net.interstellarai.unreminder.widget.WidgetRefresher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GeofenceBroadcastReceiverTest {

    private val context: Context = mockk(relaxed = true)
    private val intent: Intent = mockk(relaxed = true)
    private val event: GeofencingEvent = mockk()
    private val geofenceManager: GeofenceManager = mockk(relaxUnitFun = true)
    private val widgetRefresher: WidgetRefresher = mockk(relaxUnitFun = true)
    private val currentLocationIds = MutableStateFlow<Set<Long>>(emptySet())

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
        every { geofenceManager.currentLocationIds } returns currentLocationIds

        mockkStatic(GeofencingEvent::class)
        every { GeofencingEvent.fromIntent(intent) } returns event
        every { event.hasError() } returns false

        mockkStatic(Sentry::class)
        every { Sentry.captureMessage(any(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID
    }

    @After
    fun tearDown() {
        unmockkStatic(GeofencingEvent::class)
        unmockkStatic(Sentry::class)
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

        verify(exactly = 1) { geofenceManager.addLocationId(5L, LocationSetChangeCause.ENTER) }
        verify(exactly = 1) { widgetRefresher.refresh() }
    }

    @Test
    fun `leaving a geofence refreshes the widget once for the whole batch`() {
        givenTransition(Geofence.GEOFENCE_TRANSITION_EXIT, "5", "6")

        receiver.onReceive(context, intent)

        verify(exactly = 1) { geofenceManager.removeLocationId(5L, LocationSetChangeCause.EXIT) }
        verify(exactly = 1) { geofenceManager.removeLocationId(6L, LocationSetChangeCause.EXIT) }
        verify(exactly = 1) { widgetRefresher.refresh() }
    }

    @Test
    fun `a transition with no usable geofence leaves the widget alone`() {
        givenTransition(Geofence.GEOFENCE_TRANSITION_ENTER, "not-an-id")

        receiver.onReceive(context, intent)

        verify(exactly = 0) { widgetRefresher.refresh() }
    }

    @Test
    fun `every transition is reported with its type, location ids and resulting set size`() {
        val callback = slot<ScopeCallback>()
        every { Sentry.captureMessage("Geofence transition", capture(callback)) } returns SentryId.EMPTY_ID
        currentLocationIds.value = setOf(5L, 6L, 9L)
        givenTransition(Geofence.GEOFENCE_TRANSITION_ENTER, "5", "6")

        receiver.onReceive(context, intent)

        val tags = mutableMapOf<String, String>()
        val extras = mutableMapOf<String, String>()
        val scope = mockk<IScope>(relaxed = true)
        every { scope.setTag(any(), any()) } answers { tags[firstArg()] = secondArg() }
        every { scope.setExtra(any(), any()) } answers { extras[firstArg()] = secondArg() }
        callback.captured.run(scope)

        assertEquals(mapOf("component" to "geofence", "transition" to "ENTER"), tags)
        assertEquals(mapOf("location_ids" to "[5, 6]", "resulting_set_size" to "3"), extras)
    }

    @Test
    fun `an event with no triggering geofences is still reported`() {
        every { event.geofenceTransition } returns Geofence.GEOFENCE_TRANSITION_EXIT
        every { event.triggeringGeofences } returns null

        receiver.onReceive(context, intent)

        verify(exactly = 1) { Sentry.captureMessage("Geofence transition", any<ScopeCallback>()) }
        verify(exactly = 0) { widgetRefresher.refresh() }
    }
}
