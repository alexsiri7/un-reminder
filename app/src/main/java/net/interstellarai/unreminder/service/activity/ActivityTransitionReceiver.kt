package net.interstellarai.unreminder.service.activity

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import javax.inject.Inject

@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {

    companion object {
        // Play Services writes the transition result onto this intent when it fires, so it must
        // be mutable; an immutable one arrives with nothing to extract (same as geofences, #339).
        fun getPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ActivityTransitionReceiver::class.java)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

    @Inject
    lateinit var activityRecognitionManager: ActivityRecognitionManager

    // No goAsync/DB/network here — ANR-prone in onReceive otherwise (see #137).
    override fun onReceive(context: Context, intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        // Delivered oldest first, so replaying in order leaves the newest transition recorded.
        for (event in result.transitionEvents) {
            activityRecognitionManager.recordTransition(event.activityType, event.transitionType)
        }
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "activity"
            message = "Activity transition"
            level = SentryLevel.INFO
            setData("event_count", result.transitionEvents.size.toString())
            setData("resolved", activityRecognitionManager.resolve().state.label)
        })
    }
}
