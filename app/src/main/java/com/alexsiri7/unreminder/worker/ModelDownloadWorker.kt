package com.alexsiri7.unreminder.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "model_download"
        const val KEY_PROGRESS = "progress"
        // Replace with real CDN URL before shipping
        private const val MODEL_URL = "https://YOUR_CDN/gemma3-1b-it-int4.task"
        private const val MODEL_FILENAME = "gemma3-1b-it-int4.task"
        private const val TAG = "ModelDownloadWorker"
    }

    override suspend fun doWork(): Result {
        val modelFile = File(applicationContext.filesDir, MODEL_FILENAME)
        if (modelFile.exists()) return Result.success()

        val tmpFile = File(applicationContext.filesDir, "$MODEL_FILENAME.tmp")
        return try {
            val request = Request.Builder().url(MODEL_URL).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Download failed: HTTP ${response.code}")
                    return Result.retry()
                }
                val body = response.body ?: return Result.failure()
                val contentLength = body.contentLength()
                var bytesRead = 0L
                body.byteStream().use { input ->
                    tmpFile.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesRead += bytes
                            if (contentLength > 0) {
                                val progress = (bytesRead * 100 / contentLength).toInt()
                                setProgress(workDataOf(KEY_PROGRESS to progress))
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }
            }
            tmpFile.renameTo(modelFile)
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
