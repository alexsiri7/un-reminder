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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

        /** Cap retry attempts so a genuinely broken URL doesn't loop forever. */
        const val MAX_ATTEMPTS = 5

        // Magic-byte prefixes for the two model container formats LiteRT-LM
        // understands. Verified on-wire against the HF-hosted bundles:
        //   - `.litertlm` files begin with the ASCII bytes "LITERTLM".
        //   - `.task` files are zip containers; stored-entry zips begin with
        //     `PK\x03\x04`, empty-zips with `PK\x05\x06`.
        // Any other prefix (e.g. `<!DOCTYPE` from an HTML error body, or
        // zeroed bytes from a truncated write) means the download is corrupt
        // and `Engine.initialize()` would later throw
        // `LiteRtLmJniException: Unable to open zip archive`.
        private val MAGIC_LITERTLM = byteArrayOf(0x4C, 0x49, 0x54, 0x45, 0x52, 0x54, 0x4C, 0x4D)
        private val MAGIC_ZIP_LOCAL = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val MAGIC_ZIP_EMPTY = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
        private val KNOWN_MAGICS = listOf(MAGIC_LITERTLM, MAGIC_ZIP_LOCAL, MAGIC_ZIP_EMPTY)
    }

    override suspend fun doWork(): Result {
        if (runAttemptCount >= MAX_ATTEMPTS) {
            Log.e(TAG, "Giving up after $runAttemptCount attempts (cap=$MAX_ATTEMPTS)")
            return Result.failure()
        }

        val modelFile = File(applicationContext.filesDir, MODEL_FILENAME)
        if (modelFile.exists()) {
            // Guard against a previously-persisted corrupt download: a truncated
            // body or an HTML error page saved under the final filename would
            // otherwise cause `Engine.initialize()` to throw
            // "Unable to open zip archive" on every app start forever, because
            // the old early-return below never re-fetched.
            if (fileLooksValid(modelFile)) {
                return Result.success()
            }
            Log.w(TAG, "Existing model file failed integrity check — deleting and re-downloading")
            if (!modelFile.delete()) {
                Log.w(TAG, "Failed to delete corrupt model file: $modelFile")
            }
        }

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
            var contentLength = -1L
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Download failed: HTTP ${response.code}")
                    return if (response.code in 500..599) Result.retry() else Result.failure()
                }
                val body = response.body ?: run {
                    Log.e(TAG, "Download failed: null body on HTTP ${response.code}")
                    return Result.failure()
                }
                contentLength = body.contentLength()
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

            // Verify declared length vs on-disk length. A mid-stream drop or
            // a proxy that trickle-truncates the response will sail past the
            // write loop without throwing but leaves a short file on disk
            // that LiteRT-LM cannot open.
            if (contentLength > 0 && tmpFile.length() != contentLength) {
                Log.w(
                    TAG,
                    "Download size mismatch: got=${tmpFile.length()} expected=$contentLength"
                )
                tmpFile.delete()
                return Result.retry()
            }

            // Verify first 8 bytes match a known model-container magic. Catches
            // HTML error bodies served with a 200 status (CDN 503 → maintenance
            // page, region-locked, etc.) and any other garbage that would later
            // surface as a cryptic `Unable to open zip archive` on-device.
            if (!fileLooksValid(tmpFile)) {
                val hex = readFirst8(tmpFile).joinToString(" ") { "%02x".format(it) }
                Log.w(TAG, "Download magic bytes unrecognised (got: $hex) — deleting and retrying")
                tmpFile.delete()
                return Result.retry()
            }

            // Atomic rename — on API 26+ `Files.move` with ATOMIC_MOVE guarantees
            // we never observe a partially-populated final filename even if the
            // process is killed mid-operation. minSdk = 31 for this app.
            try {
                Files.move(
                    tmpFile.toPath(),
                    modelFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Atomic rename failed: $tmpFile -> $modelFile", e)
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

    /**
     * True iff [file] starts with one of [KNOWN_MAGICS]. Non-existent or
     * unreadable files are treated as invalid.
     */
    private fun fileLooksValid(file: File): Boolean {
        if (!file.exists() || file.length() < MAGIC_LITERTLM.size) return false
        val first8 = readFirst8(file)
        return KNOWN_MAGICS.any { expected ->
            first8.take(expected.size).toByteArray().contentEquals(expected)
        }
    }

    private fun readFirst8(file: File): ByteArray {
        val buf = ByteArray(MAGIC_LITERTLM.size)
        return try {
            file.inputStream().use { it.read(buf) }
            buf
        } catch (_: Exception) {
            ByteArray(MAGIC_LITERTLM.size)
        }
    }
}
