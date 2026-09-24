package net.interstellarai.unreminder.service.worker

import android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.interstellarai.unreminder.data.repository.GenerationFailure
import net.interstellarai.unreminder.data.repository.GenerationFailureRepository
import net.interstellarai.unreminder.data.repository.WorkerTokenRepository
import net.interstellarai.unreminder.di.DeviceLabel
import net.interstellarai.unreminder.di.WorkerUrl
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RegistrationOutcome {
    data class Registered(val tokenId: String) : RegistrationOutcome
    data class Failed(val kind: GenerationFailure.Kind) : RegistrationOutcome
}

/**
 * Obtains this install's Worker token through Play Integrity (req 020) when none is stored, and
 * drops a self-registered token the Worker has revoked. Every failure is left in
 * [GenerationFailureRepository] for Cloud AI settings, and holds off background attempts for a
 * while so they spend neither the Worker's daily registration cap nor Play's quota.
 */
@Singleton
class WorkerRegistrar @Inject constructor(
    private val requestyProxyClient: RequestyProxyClient,
    private val workerTokenRepository: WorkerTokenRepository,
    private val generationFailureRepository: GenerationFailureRepository,
    @WorkerUrl private val workerUrl: String,
    @DeviceLabel private val deviceLabel: String,
) {
    private val mutex = Mutex()

    /**
     * The stored token, registering one first when there is none; "" when that failed or the
     * build has no Worker. Background callers respect the backoff a failure set; onboarding
     * passes [ignoreBackoff] because its one attempt often follows a failed background one by
     * seconds.
     */
    suspend fun ensureToken(ignoreBackoff: Boolean = false): String {
        workerTokenRepository.token.first().let { if (it.isNotBlank()) return it }
        if (workerUrl.isBlank()) return ""
        return mutex.withLock {
            val stored = workerTokenRepository.token.first()
            when {
                stored.isNotBlank() -> stored
                !ignoreBackoff && isBackingOff() -> ""
                else -> {
                    registerLocked()
                    workerTokenRepository.token.first()
                }
            }
        }
    }

    /** Registers now, replacing any stored token if it succeeds. */
    suspend fun register(): RegistrationOutcome {
        if (workerUrl.isBlank()) return RegistrationOutcome.Failed(GenerationFailure.Kind.SERVICE_UNAVAILABLE)
        return mutex.withLock { registerLocked() }
    }

    /**
     * Clears [token] after the Worker rejected it, so the next attempt registers afresh; true if
     * it did. Kept instead: a pasted token, since this install presumably cannot register; one
     * registered within [REREGISTER_GRACE], so a Worker that rejects fresh tokens costs one mint
     * an hour rather than a loop; and any token other than [token], since that one is newer.
     * A failed write counts as nothing cleared, so the caller's 401 handler still records the
     * rejection.
     */
    suspend fun discardRejected(token: String): Boolean = mutex.withLock {
        val registration = workerTokenRepository.selfRegistration.first() ?: return@withLock false
        if (registration.registeredAt.isAfter(Instant.now().minus(REREGISTER_GRACE))) return@withLock false
        try {
            workerTokenRepository.clearIfCurrent(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "could not clear the rejected token", e)
            false
        }
    }

    private suspend fun isBackingOff(): Boolean =
        workerTokenRepository.registrationRetryAt.first()?.isAfter(Instant.now()) == true

    private suspend fun registerLocked(): RegistrationOutcome {
        val failure = try {
            val registration = requestyProxyClient.register(deviceLabel, workerUrl)
            if (!WorkerToken.isWellFormed(registration.token)) {
                throw WorkerError(200, "Malformed token in registration response")
            }
            workerTokenRepository.setRegisteredToken(registration.token, deviceLabel, Instant.now())
            clearFailureRegistrationResolves()
            val tokenId = WorkerToken.displayId(registration.token)
            Log.i(TAG, "Registered as $tokenId")
            return RegistrationOutcome.Registered(tokenId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classify(e)
        }
        generationFailureRepository.record(GenerationFailure(failure.kind, null, Instant.now()))
        try {
            workerTokenRepository.deferRegistration(Instant.now().plus(failure.retryIn))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "could not defer registration", e)
        }
        return RegistrationOutcome.Failed(failure.kind)
    }

    /** A spend-cap record stays: a new token says nothing about spend. */
    private suspend fun clearFailureRegistrationResolves() {
        val kind = generationFailureRepository.failure.first()?.kind ?: return
        if (kind.isTokenProblem || kind == GenerationFailure.Kind.SERVICE_UNAVAILABLE) {
            generationFailureRepository.clear()
        }
    }

    private data class Failure(val kind: GenerationFailure.Kind, val retryIn: Duration)

    private fun classify(e: Exception): Failure {
        val failure = when (e) {
            is IntegrityUnavailableException ->
                if (e.retryable) Failure(GenerationFailure.Kind.SERVICE_UNAVAILABLE, TRANSIENT_BACKOFF)
                else Failure(GenerationFailure.Kind.INTEGRITY_UNAVAILABLE, REFUSAL_BACKOFF)
            is WorkerIntegrityException -> Failure(GenerationFailure.Kind.REGISTRATION_REJECTED, REFUSAL_BACKOFF)
            is RegistrationCapException -> Failure(GenerationFailure.Kind.REGISTRATION_CAP, REFUSAL_BACKOFF)
            // A 429 other than the cap is the Worker's per-client rate limit.
            is WorkerError ->
                if (e.isServerError() || e.code == 429) Failure(GenerationFailure.Kind.SERVICE_UNAVAILABLE, TRANSIENT_BACKOFF)
                else unexpected(e)
            is IOException -> Failure(GenerationFailure.Kind.SERVICE_UNAVAILABLE, TRANSIENT_BACKOFF)
            else -> unexpected(e)
        }
        Log.w(TAG, "Registration failed (${failure.kind}), background retry in ${failure.retryIn}", e)
        return failure
    }

    private fun unexpected(e: Exception): Failure {
        Sentry.captureException(e) { scope -> scope.setTag("component", "worker-registrar") }
        return Failure(GenerationFailure.Kind.SERVICE_UNAVAILABLE, REFUSAL_BACKOFF)
    }

    companion object {
        private const val TAG = "WorkerRegistrar"
        val TRANSIENT_BACKOFF: Duration = Duration.ofMinutes(15)
        val REFUSAL_BACKOFF: Duration = Duration.ofHours(6)
        val REREGISTER_GRACE: Duration = Duration.ofHours(1)

        /** Mirrors `MAX_DEVICE_LABEL_LENGTH` in worker/src/routes/register.ts, counted in code points. */
        const val MAX_DEVICE_LABEL_LENGTH = 40
    }
}

/**
 * The label this device registers under, sanitized the way worker/src/routes/register.ts does so
 * the one stored here matches the one Alex sees in the token listing. The maker is prefixed unless
 * the model already starts with it: "OnePlus" + "OnePlus 12" reads "OnePlus 12".
 */
internal fun deviceLabel(manufacturer: String?, model: String?): String {
    val maker = manufacturer.orEmpty().trim()
    val name = model.orEmpty().trim()
    val raw = if (name.startsWith(maker, ignoreCase = true)) name else "$maker $name"
    val collapsed = raw.replace(CONTROL_CHARS, " ").replace(WHITESPACE_RUN, " ").trim()
    val capped = if (collapsed.codePointCount(0, collapsed.length) <= WorkerRegistrar.MAX_DEVICE_LABEL_LENGTH) collapsed
    else collapsed.substring(0, collapsed.offsetByCodePoints(0, WorkerRegistrar.MAX_DEVICE_LABEL_LENGTH))
    return capped.trim().ifEmpty { "android" }
}

private val CONTROL_CHARS = Regex("[\\p{Cc}\\p{Cf}]")
private val WHITESPACE_RUN = Regex("(?U)\\s+")
