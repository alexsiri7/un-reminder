package net.interstellarai.unreminder.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import net.interstellarai.unreminder.service.llm.ModelConfig
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Named

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    @Named("modelCdnUrl") private val modelUrl: String
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "model_download"
        const val KEY_PROGRESS = "progress"
        const val MODEL_FILENAME = "gemma3-1b-it-int4.task"
        private const val TAG = "ModelDownloadWorker"
    }

    override suspend fun doWork(): Result {
        val modelFile = File(applicationContext.filesDir, MODEL_FILENAME)
        if (modelFile.exists()) return Result.success()

        if (ModelConfig.isPlaceholderUrl(modelUrl)) {
            Log.w(
                TAG,
                "MODEL_CDN_URL is a placeholder ($modelUrl) — skipping download. " +
                    "Set the MODEL_CDN_URL secret in CI."
            )
            return Result.failure()
        }

        val tmpFile = File(applicationContext.filesDir, "$MODEL_FILENAME.tmp")
        return try {
            val request = Request.Builder().url(modelUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Download failed: HTTP ${response.code}")
                    return if (response.code in 500..599) Result.retry() else Result.failure()
                }
                val body = response.body ?: run {
                    Log.e(TAG, "Download failed: null body on HTTP ${response.code}")
                    return Result.failure()
                }
                val contentLength = body.contentLength()
                var bytesRead = 0L
                var lastReportedProgress = -1
                body.byteStream().use { input ->
                    tmpFile.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesRead += bytes
                            if (contentLength > 0) {
                                val progress = (bytesRead * 100 / contentLength).toInt()
                                if (progress != lastReportedProgress) {
                                    lastReportedProgress = progress
                                    @Suppress("CheckResult")
                                    setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                                }
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }
            }
            if (!tmpFile.renameTo(modelFile)) {
                Log.w(TAG, "Rename failed: $tmpFile -> $modelFile")
                tmpFile.delete()
                return Result.retry()
            }
            Result.success()
        } catch (e: CancellationException) {
            tmpFile.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Model download failed", e)
            tmpFile.delete()
            Result.retry()
        }
    }
}
