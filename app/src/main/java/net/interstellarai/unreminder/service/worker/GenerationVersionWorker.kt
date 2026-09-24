package net.interstellarai.unreminder.service.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.di.WorkerUrl
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Daily check of the Worker's generation version. Every active habit whose pool was generated
 * under another version gets a paced refill, which swaps the pool once the new batch lands.
 */
@HiltWorker
class GenerationVersionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    @WorkerUrl private val workerUrl: String,
    private val workerRegistrar: WorkerRegistrar,
    private val requestyProxyClient: RequestyProxyClient,
    private val variationRepository: VariationRepository,
    private val refillScheduler: RefillScheduler,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "generation_version"
        const val INTERVAL_HOURS = 24L
        private const val TAG = "GenerationVersionWorker"

        // If INTERVAL_HOURS ever changes, switch the policy below to UPDATE so the
        // new cadence takes effect; KEEP preserves the previously-scheduled interval.
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<GenerationVersionWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    override suspend fun doWork(): Result {
        // Unlike the event-driven RefillWorker this runs on every install, which makes it the
        // self-heal path for an install with no token yet. A build with no Worker URL, or a
        // registration that failed (the registrar already recorded why), must be a quiet no-op
        // rather than a daily Sentry event.
        if (workerUrl.isBlank() || workerRegistrar.ensureToken().isBlank()) return Result.success()

        return try {
            val version = requestyProxyClient.generationVersion(workerUrl)
            if (version != VariationEntity.UNVERSIONED) {
                refillScheduler.enqueuePaced(variationRepository.habitIdsNeedingRegeneration(version))
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Version check unreachable, will retry", e)
            Result.retry()
        } catch (e: WorkerError) {
            if (e.isServerError()) {
                // A 503 here is a KV blip or a misconfiguration the Worker already logged.
                Log.w(TAG, "Server error ${e.code} on version check, will retry", e)
                Result.retry()
            } else {
                reportUnexpected(e)
            }
        } catch (e: Exception) {
            reportUnexpected(e)
        }
    }

    private fun reportUnexpected(e: Exception): Result {
        Log.e(TAG, "Version check failed", e)
        Sentry.captureException(e) { scope ->
            scope.setTag("component", "generation-version-worker")
        }
        return Result.failure()
    }
}
