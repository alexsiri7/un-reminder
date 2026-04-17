package com.alexsiri7.unreminder

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alexsiri7.unreminder.service.notification.NotificationHelper
import com.alexsiri7.unreminder.worker.DailySchedulerWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
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
        super.onCreate()
        notificationHelper.createNotificationChannel()
        scheduleDailyWorker()
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
