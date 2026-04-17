package com.alexsiri7.unreminder.worker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.alexsiri7.unreminder.BuildConfig
import com.alexsiri7.unreminder.data.repository.FeedbackRepository
import com.alexsiri7.unreminder.service.github.GitHubApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.UUID

@HiltWorker
class FeedbackUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val feedbackRepository: FeedbackRepository,
    private val gitHubApiService: GitHubApiService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "FeedbackUploadWorker"
        const val KEY_FEEDBACK_ID = "feedback_id"

        fun enqueue(context: Context, feedbackId: Long) {
            val request = OneTimeWorkRequestBuilder<FeedbackUploadWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setInputData(workDataOf(KEY_FEEDBACK_ID to feedbackId))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        val feedbackId = inputData.getLong(KEY_FEEDBACK_ID, -1L)
        if (feedbackId == -1L) {
            Log.w(TAG, "No feedback ID in input data")
            return Result.failure()
        }
        val pending = feedbackRepository.getPending().firstOrNull { it.id == feedbackId }
        if (pending == null) {
            Log.w(TAG, "Feedback $feedbackId not found — may already be sent")
            return Result.success()
        }

        val deviceInfo = buildDeviceInfo()
        val screenshotFile = File(pending.screenshotPath)
        val uuid = UUID.randomUUID().toString()

        return try {
            val imageUrl = if (screenshotFile.exists() && BuildConfig.GITHUB_FEEDBACK_TOKEN.isNotBlank()) {
                gitHubApiService.uploadImage(screenshotFile, uuid)
            } else null

            val success = gitHubApiService.createIssue(
                description = pending.description,
                imageUrl = imageUrl,
                deviceInfo = deviceInfo
            )

            if (success) {
                feedbackRepository.markSent(feedbackId)
                screenshotFile.delete()
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "FeedbackUploadWorker failed for id=$feedbackId", e)
            Result.retry()
        }
    }

    private fun buildDeviceInfo(): String {
        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        return "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
               "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
               "App: $versionName ($versionCode)"
    }
}
