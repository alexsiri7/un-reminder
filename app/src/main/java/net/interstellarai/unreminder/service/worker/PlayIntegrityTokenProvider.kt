package net.interstellarai.unreminder.service.worker

import android.util.Log
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Standard Play Integrity requests: one prepared provider per process, tokens requested per
 * call. A build with no [cloudProjectNumber] (local builds without the env var) never asks
 * Play and reports [IntegrityTokenResult.NotConfigured], so only an integrity-exempt Worker token
 * works from it.
 */
class PlayIntegrityTokenProvider(
    private val manager: StandardIntegrityManager,
    private val cloudProjectNumber: Long?,
) : IntegrityTokenProvider {

    private val mutex = Mutex()
    private var provider: StandardIntegrityTokenProvider? = null

    override suspend fun warmUp() {
        if (cloudProjectNumber == null) return
        try {
            prepared()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Play Integrity warm-up failed", e)
        }
    }

    override suspend fun token(requestHash: String): IntegrityTokenResult {
        if (cloudProjectNumber == null) return IntegrityTokenResult.NotConfigured
        return try {
            try {
                IntegrityTokenResult.Token(request(requestHash))
            } catch (e: StandardIntegrityException) {
                // The prepared provider has expired; prepare once more before giving up.
                if (e.errorCode != StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID) throw e
                mutex.withLock { provider = null }
                IntegrityTokenResult.Token(request(requestHash))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: StandardIntegrityException) {
            Log.w(TAG, "Play Integrity token unavailable (code ${e.errorCode})", e)
            IntegrityTokenResult.Unavailable(retryable = isRetryable(e.errorCode), errorCode = e.errorCode)
        } catch (e: Exception) {
            Log.w(TAG, "Play Integrity token request failed", e)
            Sentry.captureException(e) { scope -> scope.setTag("component", "play-integrity") }
            IntegrityTokenResult.Unavailable(retryable = false, errorCode = null)
        }
    }

    private suspend fun request(requestHash: String): String =
        prepared()
            .request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build())
            .await()
            .token()

    private suspend fun prepared(): StandardIntegrityTokenProvider = mutex.withLock {
        provider ?: manager
            .prepareIntegrityToken(
                PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(checkNotNull(cloudProjectNumber)).build()
            )
            .await()
            .also { provider = it }
    }

    private fun isRetryable(code: Int): Boolean = when (code) {
        StandardIntegrityErrorCode.NETWORK_ERROR,
        StandardIntegrityErrorCode.TOO_MANY_REQUESTS,
        StandardIntegrityErrorCode.CANNOT_BIND_TO_SERVICE,
        StandardIntegrityErrorCode.GOOGLE_SERVER_UNAVAILABLE,
        StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED,
        StandardIntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED,
        StandardIntegrityErrorCode.CLIENT_TRANSIENT_ERROR,
        StandardIntegrityErrorCode.INTERNAL_ERROR -> true
        else -> false
    }

    private companion object {
        const val TAG = "PlayIntegrity"
    }
}
