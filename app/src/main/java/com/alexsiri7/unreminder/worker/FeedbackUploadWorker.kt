package com.alexsiri7.unreminder.worker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alexsiri7.unreminder.BuildConfig
import com.alexsiri7.unreminder.data.repository.FeedbackRepository
import com.alexsiri7.unreminder.service.github.GitHubApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException

@HiltWorker
class FeedbackUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val feedbackRepository: FeedbackRepository,
    private val gitHubApiService: GitHubApiService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "feedback_upload"
        private const val TAG = "FeedbackUploadWorker"
    }

    override suspend fun doWork(): Result {
        val pending = feedbackRepository.getPending()
        if (pending.isEmpty()) return Result.success()

        if (BuildConfig.GITHUB_FEEDBACK_TOKEN.isBlank()) return Result.failure()

        for (item in pending) {
            try {
                val screenshotUrl = item.screenshotPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) gitHubApiService.uploadImage(file) else null
                }

                val title = item.description.take(60).ifBlank { "Feedback from app" }
                val body = buildString {
                    if (item.description.isNotBlank()) appendLine(item.description).appendLine()
                    if (screenshotUrl != null) appendLine("![screenshot]($screenshotUrl)").appendLine()
                    appendLine("---")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                }

                gitHubApiService.createIssue(title, body)
                feedbackRepository.deleteById(item.id)
                item.screenshotPath?.let { File(it).delete() }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Upload failed for item ${item.id}", e)
                val isTransient = e is IOException || e.cause is IOException
                return if (isTransient) Result.retry() else Result.failure()
            }
        }

        return Result.success()
    }
}
