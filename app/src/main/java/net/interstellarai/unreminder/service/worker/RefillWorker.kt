package net.interstellarai.unreminder.service.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import net.interstellarai.unreminder.BuildConfig
import net.interstellarai.unreminder.data.db.VariationEntity
import net.interstellarai.unreminder.data.repository.GenerationFailure
import net.interstellarai.unreminder.data.repository.GenerationFailureRepository
import net.interstellarai.unreminder.data.repository.HabitRepository
import net.interstellarai.unreminder.data.repository.PersonalContextRepository
import net.interstellarai.unreminder.data.repository.VariationRepository
import net.interstellarai.unreminder.domain.model.SpendCapScope
import net.interstellarai.unreminder.domain.model.SpendCapType
import net.interstellarai.unreminder.service.notification.MascotSprites
import io.sentry.Sentry
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
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
    private val personalContextRepository: PersonalContextRepository,
    private val workerRegistrar: WorkerRegistrar,
    private val generationFailureRepository: GenerationFailureRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "refill"
        const val KEY_HABIT_ID = "habit_id"
        /** True for a manual regenerate: the landed batch replaces the unconsumed pool instead of topping it up. */
        const val KEY_REPLACE = "replace"
        private const val TAG = "RefillWorker"
    }

    override suspend fun doWork(): Result {
        val habitId = inputData.getLong(KEY_HABIT_ID, -1L)
        val replace = inputData.getBoolean(KEY_REPLACE, false)
        if (habitId == -1L) {
            Log.w(TAG, "No habit_id in input data")
            return Result.failure()
        }

        val url = BuildConfig.WORKER_URL
        val token = workerRegistrar.ensureToken()
        if (token.isBlank()) {
            Log.w(TAG, "No worker token and none could be registered, skipping refill for habit $habitId")
            return Result.failure()
        }

        val habit = habitRepository.getByIdOnce(habitId)
        if (habit == null) {
            Log.w(TAG, "Habit $habitId not found, skipping refill")
            return Result.failure()
        }

        val personalContext = personalContextRepository.personalContext.first()
        val promptFingerprint =
            "${habit.name}|${habit.descriptionLadder.joinToString("|")}|$personalContext"

        return try {
            val batch = requestyProxyClient.generateBatch(
                habitTitle = habit.name,
                habitTags = emptyList(),
                locationName = "",
                timeOfDay = "",
                personalContext = personalContext,
                sprites = MascotSprites.entries,
                supportedModes = habit.supportedModes,
                n = VariationRepository.POOL_SIZE,
                workerUrl = url,
                workerToken = token,
            )
            val now = Instant.now()
            val entities = batch.variants.map { variant ->
                VariationEntity(
                    habitId = habitId,
                    text = variant.text,
                    promptFingerprint = promptFingerprint,
                    generatedAt = now,
                    actionUrl = variant.actionUrl,
                    spriteTag = variant.spriteTag,
                    shape = variant.shape,
                    modes = variant.modes,
                    generationVersion = batch.generationVersion,
                )
            }
            variationRepository.refill(habitId, batch.generationVersion, entities, replace)
            generationFailureRepository.clear()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpendCapExceededException) {
            Log.w(TAG, "Spend cap exceeded for habit $habitId", e)
            // An unknown scope reads as the service's: never blame the user without the Worker saying so.
            note(
                if (e.capScope == SpendCapScope.USER) GenerationFailure.Kind.SPEND_CAP_USER
                else GenerationFailure.Kind.SPEND_CAP_GLOBAL,
                e.capType,
            )
            Result.failure()
        } catch (e: WorkerAuthException) {
            if (workerRegistrar.discardRejected(token)) {
                Log.w(TAG, "Self-registered token rejected for habit $habitId, re-registering", e)
                Result.retry()
            } else {
                Log.w(TAG, "Auth failed for habit $habitId", e)
                note(GenerationFailure.Kind.TOKEN_REJECTED)
                Result.failure()
            }
        } catch (e: WorkerIntegrityException) {
            Log.w(TAG, "Integrity check failed (${e.reason}) for habit $habitId, ${if (e.retryable) "will retry" else "giving up"}", e)
            if (e.retryable) Result.retry() else Result.failure()
        } catch (e: WorkerError) {
            if (e.isServerError()) {
                Log.w(TAG, "Server error ${e.code} for habit $habitId, will retry", e)
                Sentry.captureException(e) { scope ->
                    scope.setTag("component", "refill-worker")
                    scope.setTag("habit_id", habitId.toString())
                    scope.setTag("error_code", e.code.toString())
                }
                note(GenerationFailure.Kind.SERVICE_UNAVAILABLE)
                Result.retry()
            } else {
                Log.w(TAG, "Client error ${e.code} for habit $habitId", e)
                Result.failure()
            }
        } catch (e: UnknownHostException) {
            // Safety net: NetworkType.CONNECTED constraint prevents most cases,
            // but network can drop mid-request (handoff, captive portal redirect).
            Log.w(TAG, "DNS lookup failed for habit $habitId, will retry", e)
            note(GenerationFailure.Kind.SERVICE_UNAVAILABLE)
            Result.retry()
        } catch (e: ConnectException) {
            // Transient TCP-connect failure (network unreachable, captive portal,
            // mobile handoff). Worker is on Cloudflare — a real server-side connect
            // refusal is vanishingly improbable, so treat as offline-class noise.
            Log.w(TAG, "Connect failed for habit $habitId, will retry", e)
            note(GenerationFailure.Kind.SERVICE_UNAVAILABLE)
            Result.retry()
        } catch (e: SocketTimeoutException) {
            // Transient socket timeout (slow LTE handoff, Requesty/Cloudflare high load,
            // Doze network throttling). Same class of offline noise as UnknownHostException
            // and ConnectException — not actionable for the developer.
            Log.w(TAG, "Socket timeout for habit $habitId, will retry", e)
            note(GenerationFailure.Kind.SERVICE_UNAVAILABLE)
            Result.retry()
        } catch (e: InterruptedIOException) {
            // OkHttp callTimeout (90s) expired — same transient class as SocketTimeoutException.
            // InterruptedIOException is the parent of SocketTimeoutException; OkHttp uses it
            // specifically for the overall call deadline, not per-operation timeouts.
            Log.w(TAG, "Call timeout for habit $habitId, will retry", e)
            note(GenerationFailure.Kind.SERVICE_UNAVAILABLE)
            Result.retry()
        } catch (e: IOException) {
            Log.w(TAG, "IO error for habit $habitId, will retry", e)
            Sentry.captureException(e) { scope ->
                scope.setTag("component", "refill-worker")
                scope.setTag("habit_id", habitId.toString())
            }
            note(GenerationFailure.Kind.SERVICE_UNAVAILABLE)
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

    /** Leaves a record for Cloud AI settings; the token, cap and service classes are the only ones a user can act on. */
    private suspend fun note(kind: GenerationFailure.Kind, capType: SpendCapType? = null) =
        generationFailureRepository.record(GenerationFailure(kind, capType, Instant.now()))

    private fun reportUnexpected(e: Exception, habitId: Long): Result {
        Log.e(TAG, "Unexpected error for habit $habitId", e)
        Sentry.captureException(e) { scope ->
            scope.setTag("component", "refill-worker")
            scope.setTag("habit_id", habitId.toString())
        }
        return Result.failure()
    }
}
