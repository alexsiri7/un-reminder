package net.interstellarai.unreminder.service.activity

import android.app.Application
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.internal.GeneratedComponentManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import io.sentry.Breadcrumb
import io.sentry.Sentry
import net.interstellarai.unreminder.domain.model.ActivityResolution
import net.interstellarai.unreminder.domain.model.ActivityState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActivityTransitionReceiverTest {

    private val context: Context = mockk(relaxed = true)
    private val intent: Intent = mockk(relaxed = true)
    private val manager: ActivityRecognitionManager = mockk(relaxUnitFun = true)
    private val breadcrumbs = mutableListOf<Breadcrumb>()

    private val receiver = ActivityTransitionReceiver().apply {
        activityRecognitionManager = manager
    }

    @Before
    fun setup() {
        // The Hilt base class injects from the application on every onReceive; the field is
        // set by hand above, so the injector only has to exist.
        val injector: ActivityTransitionReceiver_GeneratedInjector = mockk(relaxUnitFun = true)
        val application = mockk<Application>(moreInterfaces = arrayOf(GeneratedComponentManager::class))
        every { (application as GeneratedComponentManager<*>).generatedComponent() } returns injector
        every { context.applicationContext } returns application
        every { manager.resolve() } returns ActivityResolution(ActivityState.Cycling, null)

        mockkStatic(ActivityTransitionResult::class)
        mockkStatic(Sentry::class)
        every { Sentry.addBreadcrumb(capture(breadcrumbs)) } just runs
    }

    @After
    fun tearDown() {
        unmockkStatic(ActivityTransitionResult::class)
        unmockkStatic(Sentry::class)
    }

    private fun givenEvents(vararg events: Pair<Int, Int>) {
        val result = ActivityTransitionResult(
            events.map { (activity, transition) -> ActivityTransitionEvent(activity, transition, 0L) },
        )
        every { ActivityTransitionResult.extractResult(intent) } returns result
    }

    @Test
    fun `every event is replayed in delivery order`() {
        givenEvents(
            DetectedActivity.STILL to ActivityTransition.ACTIVITY_TRANSITION_EXIT,
            DetectedActivity.ON_BICYCLE to ActivityTransition.ACTIVITY_TRANSITION_ENTER,
        )

        receiver.onReceive(context, intent)

        verifyOrder {
            manager.recordTransition(DetectedActivity.STILL, ActivityTransition.ACTIVITY_TRANSITION_EXIT)
            manager.recordTransition(DetectedActivity.ON_BICYCLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER)
        }
    }

    @Test
    fun `the batch is reported with its size and what it resolved to`() {
        givenEvents(DetectedActivity.ON_BICYCLE to ActivityTransition.ACTIVITY_TRANSITION_ENTER)

        receiver.onReceive(context, intent)

        val crumb = breadcrumbs.single()
        assertEquals("activity", crumb.category)
        assertEquals("Activity transition", crumb.message)
        assertEquals(mapOf("event_count" to "1", "resolved" to "cycling"), crumb.data)
    }

    @Test
    fun `an intent carrying no transition result is ignored`() {
        every { ActivityTransitionResult.extractResult(intent) } returns null

        receiver.onReceive(context, intent)

        verify(exactly = 0) { manager.recordTransition(any(), any()) }
        assertTrue(breadcrumbs.isEmpty())
    }
}
