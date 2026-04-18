package com.alexsiri7.unreminder.worker

import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alexsiri7.unreminder.BuildConfig
import com.alexsiri7.unreminder.data.repository.FeedbackRepository
import com.alexsiri7.unreminder.service.github.GitHubApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class FeedbackUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val feedbackRepository: FeedbackRepository,
    private val gitHubApiService: GitHubApiService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME_PREFIX = "feedback_upload_"
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
                return Result.retry()
            }
        }

        return Result.success()
    }
}
