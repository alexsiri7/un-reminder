package net.interstellarai.unreminder.service.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import net.interstellarai.unreminder.BuildConfig
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import io.sentry.Sentry
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.time.Instant
import org.json.JSONException

@HiltWorker
class RefillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val habitRepository: HabitRepository,
    private val variationRepository: VariationRepository,
    private val requestyProxyClient: RequestyProxyClient,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "refill"
        const val KEY_HABIT_ID = "habit_id"
        private const val TAG = "RefillWorker"
    }

    override suspend fun doWork(): Result {
        val habitId = inputData.getLong(KEY_HABIT_ID, -1L)
        if (habitId == -1L) {
            Log.w(TAG, "No habit_id in input data")
            return Result.failure()
        }

        val url = BuildConfig.WORKER_URL
        val secret = BuildConfig.WORKER_SECRET

        val habit = habitRepository.getByIdOnce(habitId)
        if (habit == null) {
            Log.w(TAG, "Habit $habitId not found, skipping refill")
            return Result.failure()
        }

        val promptFingerprint = "${habit.name}|${habit.descriptionLadder.joinToString("|")}"

        return try {
            val texts = requestyProxyClient.generateBatch(
                habitTitle = habit.name,
                habitTags = emptyList(),
                locationName = "",
                timeOfDay = "",
                n = VariationRepository.POOL_SIZE,
                workerUrl = url,
                workerSecret = secret,
            )
            val now = Instant.now()
            val entities = texts.map { text ->
                VariationEntity(
                    habitId = habitId,
                    text = text,
                    promptFingerprint = promptFingerprint,
                    generatedAt = now,
                )
            }
            variationRepository.deleteConsumedForHabit(habitId)
            variationRepository.insertAll(entities)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpendCapExceededException) {
            Log.w(TAG, "Spend cap exceeded for habit $habitId", e)
            Result.failure()
        } catch (e: WorkerAuthException) {
            Log.w(TAG, "Auth failed for habit $habitId", e)
            Result.failure()
        } catch (e: WorkerError) {
            if (e.isServerError()) {
                Log.w(TAG, "Server error ${e.code} for habit $habitId, will retry", e)
                Sentry.captureException(e) { scope ->
                    scope.setTag("component", "refill-worker")
                    scope.setTag("habit_id", habitId.toString())
                    scope.setTag("error_code", e.code.toString())
                }
                Result.retry()
            } else {
                Log.w(TAG, "Client error ${e.code} for habit $habitId", e)
                Result.failure()
            }
        } catch (e: UnknownHostException) {
            // Transient DNS failure (offline, Doze wake-up race, captive portal).
            // Not actionable for the developer — WorkManager retries with backoff.
            Log.w(TAG, "DNS lookup failed for habit $habitId, will retry", e)
            Result.retry()
        } catch (e: ConnectException) {
            // Transient TCP-connect failure (network unreachable, captive portal,
            // mobile handoff). Worker is on Cloudflare — a real server-side connect
            // refusal is vanishingly improbable, so treat as offline-class noise.
            Log.w(TAG, "Connect failed for habit $habitId, will retry", e)
            Result.retry()
        } catch (e: IOException) {
            Log.w(TAG, "IO error for habit $habitId, will retry", e)
            Sentry.captureException(e) { scope ->
                scope.setTag("component", "refill-worker")
                scope.setTag("habit_id", habitId.toString())
            }
            Result.retry()
        } catch (e: JSONException) {
            Log.w(TAG, "JSON parse error for habit $habitId, will retry", e)
            Sentry.captureException(e) { scope ->
                scope.setTag("component", "refill-worker")
                scope.setTag("habit_id", habitId.toString())
            }
            Result.retry()
        } catch (e: RuntimeException) {
            if (e.cause is JSONException) {
                Log.w(TAG, "JSON parse error (wrapped) for habit $habitId, will retry", e)
                Sentry.captureException(e) { scope ->
                    scope.setTag("component", "refill-worker")
                    scope.setTag("habit_id", habitId.toString())
                }
                Result.retry()
            } else {
                reportUnexpected(e, habitId)
            }
        } catch (e: Exception) {
            reportUnexpected(e, habitId)
        }
    }

    private fun reportUnexpected(e: Exception, habitId: Long): Result {
        Log.e(TAG, "Unexpected error for habit $habitId", e)
        Sentry.captureException(e) { scope ->
            scope.setTag("component", "refill-worker")
            scope.setTag("habit_id", habitId.toString())
        }
        return Result.failure()
    }
}
