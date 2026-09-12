package net.interstellarai.unreminder.service.geofence

import android.app.Application
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.internal.GeneratedComponentManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.protocol.SentryId
import kotlinx.coroutines.flow.MutableStateFlow
import net.interstellarai.unreminder.widget.WidgetRefresher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeofenceBroadcastReceiverTest {

    private val context: Context = mockk(relaxed = true)
    private val intent: Intent = mockk(relaxed = true)
    private val event: GeofencingEvent = mockk()
    private val geofenceManager: GeofenceManager = mockk(relaxUnitFun = true)
    private val widgetRefresher: WidgetRefresher = mockk(relaxUnitFun = true)
    private val currentLocationIds = MutableStateFlow<Set<Long>>(emptySet())
    private val breadcrumbs = mutableListOf<Breadcrumb>()

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
        every { Sentry.addBreadcrumb(capture(breadcrumbs)) } just runs
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
        currentLocationIds.value = setOf(5L, 6L, 9L)
        givenTransition(Geofence.GEOFENCE_TRANSITION_ENTER, "5", "6")

        receiver.onReceive(context, intent)

        val crumb = breadcrumbs.single()
        assertEquals("geofence", crumb.category)
        assertEquals("Geofence transition", crumb.message)
        assertEquals(SentryLevel.INFO, crumb.level)
        assertEquals(
            mapOf(
                "transition" to "ENTER",
                "location_ids" to "[5, 6]",
                "resulting_set_size" to "3",
            ),
            crumb.data
        )
    }

    @Test
    fun `an event with no triggering geofences is still reported`() {
        every { event.geofenceTransition } returns Geofence.GEOFENCE_TRANSITION_EXIT
        every { event.triggeringGeofences } returns null

        receiver.onReceive(context, intent)

        assertEquals("Geofence transition", breadcrumbs.single().message)
        verify(exactly = 0) { Sentry.captureMessage(any(), any<ScopeCallback>()) }
        verify(exactly = 0) { widgetRefresher.refresh() }
    }

    @Test
    fun `a normal transition opens no Sentry issue`() {
        givenTransition(Geofence.GEOFENCE_TRANSITION_ENTER, "5")

        receiver.onReceive(context, intent)

        verify(exactly = 0) { Sentry.captureMessage(any(), any<ScopeCallback>()) }
    }

    @Test
    fun `a geofence error is still reported as an issue`() {
        val callback = slot<ScopeCallback>()
        every { Sentry.captureMessage("Geofence error", capture(callback)) } returns SentryId.EMPTY_ID
        every { event.hasError() } returns true
        every { event.errorCode } returns GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE

        receiver.onReceive(context, intent)

        val tags = mutableMapOf<String, String>()
        val scope = mockk<IScope>(relaxed = true)
        every { scope.setTag(any(), any()) } answers { tags[firstArg()] = secondArg() }
        callback.captured.run(scope)

        assertEquals(
            mapOf(
                "component" to "geofence",
                "error_code" to GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE.toString(),
            ),
            tags
        )
        verify(exactly = 1) { scope.level = SentryLevel.ERROR }
        assertTrue(breadcrumbs.isEmpty())
    }
}
