package net.interstellarai.unreminder

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.sentry.applyOptions
import net.interstellarai.unreminder.service.sentry.shouldInitSentry
import net.interstellarai.unreminder.worker.RandomIntervalWorker
import net.interstellarai.unreminder.worker.TriggerWatchdogWorker
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import javax.inject.Inject

@HiltAndroidApp
class UnReminderApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        initSentry() // must run before super.onCreate() to capture any init-phase crashes
        super.onCreate()
        notificationHelper.createNotificationChannel()
        val workManager = WorkManager.getInstance(this)
        // Bootstrap (KEEP): guarantees a first tick within MIN/MAX_DELAY_MINUTES on a
        // fresh install or first launch, instead of waiting up to a full watchdog interval.
        RandomIntervalWorker.enqueueInitial(workManager)
        // Self-heal (KEEP, 24h periodic): re-enqueues the chain if it ever falls into a
        // terminal state, and reaps stale SCHEDULED rows.
        TriggerWatchdogWorker.enqueue(this)
    }

    private fun initSentry() {
        val dsn = BuildConfig.SENTRY_DSN
        if (!shouldInitSentry(dsn)) return
        try {
            SentryAndroid.init(this) { options ->
                applyOptions(
                    options,
                    dsn = dsn,
                    isDebug = BuildConfig.DEBUG,
                    appId = BuildConfig.APPLICATION_ID,
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Sentry init failed", e)
        }
    }

    companion object {
        private const val TAG = "UnReminderApp"
    }
}
