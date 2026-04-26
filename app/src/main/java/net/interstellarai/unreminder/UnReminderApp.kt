package net.interstellarai.unreminder

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import net.interstellarai.unreminder.service.notification.NotificationHelper
import net.interstellarai.unreminder.service.sentry.LaunchSmokeTest
import net.interstellarai.unreminder.service.sentry.applyOptions
import net.interstellarai.unreminder.service.sentry.shouldInitSentry
import net.interstellarai.unreminder.worker.RandomIntervalWorker
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
        ensureRandomIntervalWorker()
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
            LaunchSmokeTest.maybeFire(
                context = this,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Sentry init failed", e)
        }
    }

    companion object {
        private const val TAG = "UnReminderApp"
    }

    private fun ensureRandomIntervalWorker() {
        RandomIntervalWorker.ensureEnqueued(this)
    }
}
