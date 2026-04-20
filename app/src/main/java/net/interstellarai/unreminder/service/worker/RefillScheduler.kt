package net.interstellarai.unreminder.service.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefillScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enqueueForHabit(habitId: Long) {
        val request = OneTimeWorkRequestBuilder<RefillWorker>()
            .setInputData(workDataOf(RefillWorker.KEY_HABIT_ID to habitId))
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${RefillWorker.WORK_NAME}-$habitId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
