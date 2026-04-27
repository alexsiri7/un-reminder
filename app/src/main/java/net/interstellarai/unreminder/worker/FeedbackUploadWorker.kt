package net.interstellarai.unreminder.worker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.interstellarai.unreminder.BuildConfig
import net.interstellarai.unreminder.data.repository.FeedbackRepository
import net.interstellarai.unreminder.service.github.GitHubApiService
import net.interstellarai.unreminder.service.log.collectLogs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        if (BuildConfig.FEEDBACK_ENDPOINT_URL.isBlank()) return Result.failure()

        val logTail = withContext(Dispatchers.IO) { collectLogs() }

        for (item in pending) {
            try {
                val title = item.description.take(60).ifBlank { "Feedback from app" }
                val body = buildString {
                    if (item.description.isNotBlank()) appendLine(item.description).appendLine()
                    appendLine("---")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    if (logTail != null) {
                        appendLine()
                        appendLine("---")
                        appendLine("<details><summary>Logs</summary>")
                        appendLine()
                        appendLine("```")
                        appendLine(logTail)
                        appendLine("```")
                        appendLine()
                        appendLine("</details>")
                    }
                }

                val screenshotFile = item.screenshotPath
                    ?.let { File(it) }
                    ?.takeIf { it.exists() }

                gitHubApiService.submit(title, body, screenshotFile)
                feedbackRepository.deleteById(item.id)
                screenshotFile?.delete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Upload failed for item ${item.id}", e)
                val isTransient = e is IOException || e.cause is IOException
                return if (isTransient) Result.retry() else Result.failure()
            }
        }

        return Result.success()
    }
}
