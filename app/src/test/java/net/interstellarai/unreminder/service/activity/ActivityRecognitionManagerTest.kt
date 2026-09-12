package net.interstellarai.unreminder.service.activity

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import kotlinx.coroutines.test.runTest
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.ActivityResolution
import net.interstellarai.unreminder.domain.model.ActivityState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ActivityRecognitionManagerTest {

    private lateinit var context: Context
    private val client: ActivityRecognitionClient = mockk()
    private val capturedMessages = mutableListOf<String>()
    private val capturedExceptions = mutableListOf<Throwable>()

    private val sitting = ActivityState.Mode(ActivityMode.SITTING)
    private val now: Instant = Instant.parse("2026-09-12T10:00:00Z")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs()
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)

        mockkStatic(Sentry::class)
        every { Sentry.captureMessage(any(), any<ScopeCallback>()) } answers {
            capturedMessages += firstArg<String>()
            SentryId.EMPTY_ID
        }
        every { Sentry.captureException(any<Throwable>(), any<ScopeCallback>()) } answers {
            capturedExceptions += firstArg<Throwable>()
            SentryId.EMPTY_ID
        }
        every { Sentry.addBreadcrumb(any<io.sentry.Breadcrumb>()) } just runs
        every { client.requestActivityTransitionUpdates(any(), any<PendingIntent>()) } returns Tasks.forResult(null)
    }

    @After
    fun tearDown() {
        unmockkStatic(Sentry::class)
        clearPrefs()
    }

    private fun clearPrefs() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun newManager() = ActivityRecognitionManager(context, client)

    private fun observed(activityType: Int, at: Instant = now) = ActivityObservation(activityType, at)

    // ── pure resolution ──────────────────────────────────────────────────────

    @Test
    fun `no observation resolves to sitting with no age`() {
        assertEquals(ActivityResolution(sitting, null), ActivityRecognitionManager.resolve(null, now))
    }

    @Test
    fun `walking and running both resolve to the walking mode`() {
        for (type in listOf(DetectedActivity.WALKING, DetectedActivity.RUNNING)) {
            val resolution = ActivityRecognitionManager.resolve(observed(type, now.minusSeconds(30)), now)
            assertEquals(ActivityState.Mode(ActivityMode.WALKING), resolution.state)
            assertEquals(Duration.ofSeconds(30), resolution.observationAge)
        }
    }

    @Test
    fun `still resolves to sitting and a vehicle to transport`() {
        assertEquals(sitting, ActivityRecognitionManager.resolve(observed(DetectedActivity.STILL), now).state)
        assertEquals(
            ActivityState.Mode(ActivityMode.TRANSPORT),
            ActivityRecognitionManager.resolve(observed(DetectedActivity.IN_VEHICLE), now).state,
        )
    }

    @Test
    fun `a bicycle resolves to the cycling suppression, not a mode`() {
        assertEquals(
            ActivityState.Cycling,
            ActivityRecognitionManager.resolve(observed(DetectedActivity.ON_BICYCLE), now).state,
        )
    }

    @Test
    fun `unknown and tilting fall back to sitting`() {
        for (type in listOf(DetectedActivity.UNKNOWN, DetectedActivity.TILTING)) {
            assertEquals(sitting, ActivityRecognitionManager.resolve(observed(type), now).state)
        }
    }

    @Test
    fun `an observation older than the staleness window falls back to sitting but keeps its age`() {
        val age = ActivityRecognitionManager.STALENESS_WINDOW.plusSeconds(1)

        val resolution = ActivityRecognitionManager.resolve(observed(DetectedActivity.ON_BICYCLE, now.minus(age)), now)

        assertEquals(ActivityResolution(sitting, age), resolution)
    }

    @Test
    fun `an observation exactly at the staleness window is still trusted`() {
        val age = ActivityRecognitionManager.STALENESS_WINDOW

        val resolution = ActivityRecognitionManager.resolve(observed(DetectedActivity.WALKING, now.minus(age)), now)

        assertEquals(ActivityState.Mode(ActivityMode.WALKING), resolution.state)
    }

    @Test
    fun `an observation stamped in the future reads as fresh (clock skew)`() {
        val resolution = ActivityRecognitionManager.resolve(observed(DetectedActivity.WALKING, now.plusSeconds(90)), now)

        assertEquals(ActivityResolution(ActivityState.Mode(ActivityMode.WALKING), Duration.ZERO), resolution)
    }

    // ── recording ────────────────────────────────────────────────────────────

    @Test
    fun `an enter transition is recorded and resolved`() {
        val mgr = newManager()

        mgr.recordTransition(DetectedActivity.ON_BICYCLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER)

        assertEquals(DetectedActivity.ON_BICYCLE, mgr.lastObservation.value?.activityType)
        assertEquals(ActivityState.Cycling, mgr.resolve().state)
    }

    @Test
    fun `exiting the recorded activity clears it so the suppression lifts at once`() {
        val mgr = newManager()
        mgr.recordTransition(DetectedActivity.ON_BICYCLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER)

        mgr.recordTransition(DetectedActivity.ON_BICYCLE, ActivityTransition.ACTIVITY_TRANSITION_EXIT)

        assertNull(mgr.lastObservation.value)
        assertEquals(ActivityResolution(sitting, null), mgr.resolve())
    }

    @Test
    fun `exiting some other activity leaves the record alone`() {
        val mgr = newManager()
        mgr.recordTransition(DetectedActivity.WALKING, ActivityTransition.ACTIVITY_TRANSITION_ENTER)

        mgr.recordTransition(DetectedActivity.STILL, ActivityTransition.ACTIVITY_TRANSITION_EXIT)

        assertEquals(DetectedActivity.WALKING, mgr.lastObservation.value?.activityType)
    }

    @Test
    fun `the last observation survives a new process`() {
        newManager().recordTransition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER)

        val restored = newManager().lastObservation.value

        assertEquals(DetectedActivity.IN_VEHICLE, restored?.activityType)
    }

    @Test
    fun `a cleared observation stays cleared in a new process`() {
        val mgr = newManager()
        mgr.recordTransition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER)
        mgr.recordTransition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_EXIT)

        assertNull(newManager().lastObservation.value)
    }

    // ── permission ───────────────────────────────────────────────────────────

    @Test
    fun `a revoked permission resolves to sitting even over a fresh observation`() {
        val mgr = newManager()
        mgr.recordTransition(DetectedActivity.ON_BICYCLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER)
        shadowOf(context as Application).denyPermissions(Manifest.permission.ACTIVITY_RECOGNITION)

        assertEquals(ActivityResolution(sitting, null), mgr.resolve())
    }

    @Test
    fun `no permission means no subscription is requested`() = runTest {
        shadowOf(context as Application).denyPermissions(Manifest.permission.ACTIVITY_RECOGNITION)

        newManager().requestTransitionUpdates()

        verify(exactly = 0) { client.requestActivityTransitionUpdates(any(), any<PendingIntent>()) }
    }

    // ── subscription ─────────────────────────────────────────────────────────

    @Test
    fun `subscribes to enter and exit for every tracked activity`() = runTest {
        val request = slot<ActivityTransitionRequest>()
        every { client.requestActivityTransitionUpdates(capture(request), any<PendingIntent>()) } returns
            Tasks.forResult(null)

        newManager().requestTransitionUpdates()

        // The request exposes no getter, so this pins the order as well as the set.
        val expected = ActivityTransitionRequest(
            listOf(
                DetectedActivity.WALKING,
                DetectedActivity.RUNNING,
                DetectedActivity.STILL,
                DetectedActivity.IN_VEHICLE,
                DetectedActivity.ON_BICYCLE,
            ).flatMap { type ->
                listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .map { transition ->
                        ActivityTransition.Builder().setActivityType(type).setActivityTransition(transition).build()
                    }
            },
        )
        assertEquals(expected, request.captured)
    }

    @Test
    fun `a rejected subscription is reported, not thrown`() = runTest {
        every { client.requestActivityTransitionUpdates(any(), any<PendingIntent>()) } returns
            Tasks.forException(ApiException(Status(CommonStatusCodes.API_NOT_CONNECTED)))

        newManager().requestTransitionUpdates()

        assertEquals(1, capturedExceptions.size)
    }

    @Test
    fun `a subscription that never settles is reported as a timeout instead of hanging`() = runTest {
        val neverSettled = TaskCompletionSource<Void>()
        every { client.requestActivityTransitionUpdates(any(), any<PendingIntent>()) } returns neverSettled.task

        newManager().requestTransitionUpdates()

        assertEquals(listOf("Activity transition updates request timed out"), capturedMessages)
    }

    private companion object {
        private const val PREFS_NAME = "activity_prefs"
    }
}
