package net.interstellarai.unreminder.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import net.interstellarai.unreminder.R
import net.interstellarai.unreminder.data.repository.ModelDownloadProgressRepository
import net.interstellarai.unreminder.service.llm.ModelConfig
import net.interstellarai.unreminder.service.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Named

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    @Named("modelCdnUrl") private val modelUrl: String,
    private val progressRepository: ModelDownloadProgressRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "model_download"
        const val KEY_PROGRESS = "progress"
        const val MODEL_FILENAME = "gemma3-1b-it-int4.task"
        private const val TAG = "ModelDownloadWorker"

        // Foreground-service notification id. Constant — the FGS is a singleton
        // (WorkManager serialises the unique work "model_download") so reusing
        // one id is fine and keeps the channel clean.
        private const val NOTIF_ID = 42_001

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

    /**
     * Called once by WorkManager before [doWork] so the worker has a valid
     * foreground-service notification while it downloads. Returning a
     * [ForegroundInfo] lets Android treat our SystemJobService instance as a
     * short-lived FGS, exempt from background-execution throttling — without
     * this, a 2.5 GB download is killed the moment the app leaves the
     * foreground.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo(progress = -1)

    override suspend fun doWork(): Result {
        if (runAttemptCount >= MAX_ATTEMPTS) {
            Log.e(TAG, "Giving up after $runAttemptCount attempts (cap=$MAX_ATTEMPTS)")
            return Result.failure()
        }

        // Promote to FGS up-front so the OS knows to keep us alive even if the
        // user presses HOME before the first progress tick lands.
        runCatching { setForeground(createForegroundInfo(progress = -1)) }
            .onFailure { Log.w(TAG, "setForeground(initial) failed — FGS may be throttled", it) }

        val modelFile = File(applicationContext.filesDir, MODEL_FILENAME)
        if (modelFile.exists()) {
            // Guard against a previously-persisted corrupt download: a truncated
            // body or an HTML error page saved under the final filename would
            // otherwise cause `Engine.initialize()` to throw
            // "Unable to open zip archive" on every app start forever, because
            // the old early-return below never re-fetched.
            if (fileLooksValid(modelFile)) {
                progressRepository.clear()
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
            // Resume-from-partial: if a previous attempt wrote bytes to `.tmp`,
            // request only the remainder via a Range header instead of restarting
            // from byte 0. This is the single biggest win when the OS kills the
            // worker midway — without it, WorkManager's retry mechanism wastes
            // every previously-downloaded byte. See RFC 7233 §3.1.
            val existingBytes = if (tmpFile.exists()) tmpFile.length() else 0L
            val requestBuilder = Request.Builder().url(modelUrl)
            if (existingBytes > 0) {
                requestBuilder.addHeader("Range", "bytes=$existingBytes-")
                Log.i(TAG, "Resuming download from byte $existingBytes")
            }
            val request = requestBuilder.build()

            // `expectedTotalLength` is what the *entire* file should be, not
            // just what this HTTP response carries. For a 206 Partial Content
            // response the Content-Length is the remainder; we add the bytes
            // already on disk to recover the whole-file size.
            var expectedTotalLength = -1L
            okHttpClient.newCall(request).execute().use { response ->
                // 200 = full download (server ignored Range or tmp was empty)
                // 206 = Partial Content — our Range header was honoured
                // Any other non-2xx = transient (5xx → retry) or hard (4xx → fail).
                when {
                    response.code == 200 -> {
                        if (existingBytes > 0) {
                            Log.w(TAG, "Server ignored Range header (HTTP 200) — restarting from 0")
                            // Truncate tmp so the append-mode write below doesn't
                            // concatenate the full body onto partial bytes.
                            tmpFile.delete()
                        }
                    }
                    response.code == 206 -> {
                        if (existingBytes == 0L) {
                            Log.w(TAG, "Got HTTP 206 without a Range header — treating as 200")
                        }
                    }
                    response.code == 416 -> {
                        // Range not satisfiable — our tmp is already >= total size.
                        // Either the file is already complete or corrupt; delete
                        // and retry from scratch.
                        Log.w(TAG, "HTTP 416 Range Not Satisfiable — wiping tmp and retrying")
                        tmpFile.delete()
                        return Result.retry()
                    }
                    !response.isSuccessful -> {
                        Log.w(TAG, "Download failed: HTTP ${response.code}")
                        return if (response.code in 500..599) Result.retry() else Result.failure()
                    }
                }
                val body = response.body ?: run {
                    Log.e(TAG, "Download failed: null body on HTTP ${response.code}")
                    return Result.failure()
                }
                val bodyLength = body.contentLength()
                // For 206, bodyLength is the remaining bytes; for 200, it's the whole file.
                val startOffset = if (response.code == 206) existingBytes else 0L
                expectedTotalLength = if (bodyLength > 0) startOffset + bodyLength else -1L

                var bytesWritten = startOffset
                var lastReportedProgress = -1
                body.byteStream().use { input ->
                    // append mode when resuming, overwrite otherwise
                    FileOutputStream(tmpFile, /*append=*/ startOffset > 0).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesWritten += bytes
                            if (expectedTotalLength > 0) {
                                val progress =
                                    (bytesWritten * 100 / expectedTotalLength).toInt().coerceIn(0, 100)
                                if (progress != lastReportedProgress) {
                                    lastReportedProgress = progress
                                    @Suppress("CheckResult")
                                    setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                                    // Persist so cold starts can render the
                                    // banner immediately, before WorkManager's
                                    // flow catches up. See repository docs.
                                    progressRepository.write(progress / 100f)
                                    // Re-issue the foreground notification with
                                    // fresh progress on every 1% tick. This
                                    // doubles as a keep-alive that prevents the
                                    // OS from deciding our FGS is stale.
                                    runCatching { setForeground(createForegroundInfo(progress)) }
                                        .onFailure {
                                            Log.w(TAG, "setForeground(update) failed", it)
                                        }
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
            if (expectedTotalLength > 0 && tmpFile.length() != expectedTotalLength) {
                Log.w(
                    TAG,
                    "Download size mismatch: got=${tmpFile.length()} expected=$expectedTotalLength",
                )
                // Keep the partial around so the next retry can resume from it
                // if length is < expected. If length is > expected we delete
                // because the file is unrecoverable.
                if (tmpFile.length() > expectedTotalLength) {
                    tmpFile.delete()
                }
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
            progressRepository.clear()
            Result.success()
        } catch (e: CancellationException) {
            // NB: don't delete tmpFile on cancellation — it holds the partial
            // bytes we want to resume from on the next WorkManager retry.
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Model download failed", e)
            // Same rationale: keep partial data so `Range:` resume works.
            Result.retry()
        }
    }

    /**
     * Builds the FGS notification with the current [progress] percent.
     * `progress` < 0 renders an indeterminate spinner ("Preparing…"); the
     * 0..100 range renders a determinate progress bar. Keeps the notification
     * silent (IMPORTANCE_LOW channel) and only-alert-once so we don't spam
     * the shade on every 1% tick.
     */
    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val indeterminate = progress !in 0..100
        val notification = NotificationCompat.Builder(
            applicationContext,
            NotificationHelper.MODEL_DOWNLOAD_CHANNEL_ID,
        )
            .setContentTitle("Downloading AI model")
            .setContentText(if (indeterminate) "Preparing…" else "$progress%")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .build()

        // API 34+ requires a declared FGS *type*. DATA_SYNC is the documented
        // match for "this is a one-shot download we need to keep alive". On
        // older API levels the type arg is a no-op.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        // POST_NOTIFICATIONS is a runtime permission on API 33+. If it's denied
        // the FGS still runs — the notification is just invisible to the user.
        // Log a breadcrumb so Sentry can surface denials when debugging.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.i(TAG, "POST_NOTIFICATIONS not granted — FGS runs, notification hidden")
            }
        }

        return ForegroundInfo(NOTIF_ID, notification, type)
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
