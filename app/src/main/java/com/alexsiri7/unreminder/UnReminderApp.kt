package com.alexsiri7.unreminder

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alexsiri7.unreminder.service.llm.PromptGenerator
import com.alexsiri7.unreminder.service.notification.NotificationHelper
import com.alexsiri7.unreminder.service.sentry.LaunchSmokeTest
import com.alexsiri7.unreminder.service.sentry.applyOptions
import com.alexsiri7.unreminder.service.sentry.shouldInitSentry
import com.alexsiri7.unreminder.worker.DailySchedulerWorker
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class UnReminderApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var promptGenerator: PromptGenerator

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        initSentry() // must run before super.onCreate() to capture any init-phase crashes
        super.onCreate()
        notificationHelper.createNotificationChannel()
        scheduleDailyWorker()
        appScope.launch { promptGenerator.initialize() }
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

    private fun scheduleDailyWorker() {
        val dailyWork = PeriodicWorkRequestBuilder<DailySchedulerWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailySchedulerWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWork
        )
    }
}
