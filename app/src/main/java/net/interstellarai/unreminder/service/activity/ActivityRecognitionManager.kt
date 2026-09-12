package net.interstellarai.unreminder.service.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.ActivityResolution
import net.interstellarai.unreminder.domain.model.ActivityState
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The last activity transition Play Services delivered, and when it was recorded. */
data class ActivityObservation(val activityType: Int, val at: Instant)

/**
 * Keeps the last observed activity so a trigger can resolve its [ActivityState] synchronously.
 * Transitions are pushed by [ActivityTransitionReceiver]; nothing here waits on a sensor.
 */
@Singleton
class ActivityRecognitionManager @Inject constructor(
    private val context: Context,
    private val activityRecognitionClient: ActivityRecognitionClient,
) {
    companion object {
        private const val TAG = "ActivityRecognitionMgr"
        private const val PREFS_NAME = "activity_prefs"
        private const val KEY_ACTIVITY_TYPE = "last_activity_type"
        private const val KEY_OBSERVED_AT = "last_activity_at"

        // Trigger cadence bottoms out at 15 minutes (req 001): an observation older than that
        // has had a whole interval to stop being true, and transitions already lag by minutes.
        internal val STALENESS_WINDOW: Duration = Duration.ofMinutes(15)

        // Runs at app start next to geofence registration; a Play Services task that never
        // settles must not hold the rest of start-up.
        private const val PLAY_SERVICES_TIMEOUT_MS = 30_000L

        private val TRACKED_ACTIVITIES = listOf(
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.STILL,
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
        )

        private val SITTING = ActivityState.Mode(ActivityMode.SITTING)

        internal fun resolve(observation: ActivityObservation?, now: Instant): ActivityResolution {
            observation ?: return ActivityResolution(SITTING, null)
            val age = maxOf(Duration.ZERO, Duration.between(observation.at, now))
            if (age > STALENESS_WINDOW) return ActivityResolution(SITTING, age)
            return ActivityResolution(stateOf(observation.activityType), age)
        }

        private fun stateOf(activityType: Int): ActivityState = when (activityType) {
            DetectedActivity.WALKING, DetectedActivity.RUNNING -> ActivityState.Mode(ActivityMode.WALKING)
            DetectedActivity.STILL -> SITTING
            DetectedActivity.IN_VEHICLE -> ActivityState.Mode(ActivityMode.TRANSPORT)
            DetectedActivity.ON_BICYCLE -> ActivityState.Cycling
            // Sitting is the common case; a state that matched no habit would make the app
            // look broken rather than quiet whenever the platform is unsure.
            else -> SITTING
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _lastObservation = MutableStateFlow(restoreObservation())

    /** Survives process death: the transition may be far older than the process reading it. */
    val lastObservation: StateFlow<ActivityObservation?> = _lastObservation.asStateFlow()

    private fun restoreObservation(): ActivityObservation? {
        val at = prefs.getLong(KEY_OBSERVED_AT, 0L).takeIf { it > 0L } ?: return null
        return ActivityObservation(prefs.getInt(KEY_ACTIVITY_TYPE, DetectedActivity.UNKNOWN), Instant.ofEpochMilli(at))
    }

    /**
     * Applies one delivered transition. An exit clears the record rather than leaving it to age
     * out, so the cycling suppression lifts when the ride ends instead of fifteen minutes later.
     */
    fun recordTransition(activityType: Int, transitionType: Int) {
        when (transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER ->
                publish(ActivityObservation(activityType, Instant.now().truncatedTo(ChronoUnit.MILLIS)))
            ActivityTransition.ACTIVITY_TRANSITION_EXIT ->
                if (_lastObservation.value?.activityType == activityType) publish(null)
        }
    }

    private fun publish(observation: ActivityObservation?) {
        _lastObservation.value = observation
        val editor = prefs.edit()
        if (observation == null) {
            editor.remove(KEY_ACTIVITY_TYPE).remove(KEY_OBSERVED_AT)
        } else {
            editor.putInt(KEY_ACTIVITY_TYPE, observation.activityType)
                .putLong(KEY_OBSERVED_AT, observation.at.toEpochMilli())
        }
        editor.apply()
    }

    /**
     * The activity state for a trigger about to fire. A revoked permission makes the stored
     * observation unreachable rather than merely old, so it answers the fallback outright.
     */
    fun resolve(): ActivityResolution =
        if (hasPermission()) resolve(_lastObservation.value, Instant.now()) else ActivityResolution(SITTING, null)

    /**
     * Subscribes to enter and exit for every tracked activity. Play Services drops the
     * subscription on app update or force-stop, so this runs on every launch and again after
     * the permission is granted — the same contract as geofence registration.
     */
    suspend fun requestTransitionUpdates() {
        if (!hasPermission()) {
            Log.i(TAG, "ACTIVITY_RECOGNITION not granted; activity mode stays on the sitting fallback")
            return
        }
        try {
            @Suppress("MissingPermission")
            val task = activityRecognitionClient.requestActivityTransitionUpdates(
                transitionRequest(),
                ActivityTransitionReceiver.getPendingIntent(context),
            )
            val registered = withTimeoutOrNull(PLAY_SERVICES_TIMEOUT_MS) { task.await(); true } ?: false
            if (!registered) {
                Log.e(TAG, "Activity transition updates request timed out")
                Sentry.captureMessage("Activity transition updates request timed out") { scope ->
                    scope.setTag("component", "activity-recognition")
                    scope.level = SentryLevel.WARNING
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Activity transition updates request failed", e)
            Sentry.captureException(e) { scope -> scope.setTag("component", "activity-recognition") }
        }
    }

    private fun transitionRequest(): ActivityTransitionRequest {
        val transitionTypes = listOf(
            ActivityTransition.ACTIVITY_TRANSITION_ENTER,
            ActivityTransition.ACTIVITY_TRANSITION_EXIT,
        )
        return ActivityTransitionRequest(
            TRACKED_ACTIVITIES.flatMap { activity ->
                transitionTypes.map { transition ->
                    ActivityTransition.Builder()
                        .setActivityType(activity)
                        .setActivityTransition(transition)
                        .build()
                }
            },
        )
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
}
