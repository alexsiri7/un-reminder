package net.interstellarai.unreminder.service.worker

sealed interface IntegrityTokenResult {
    data class Token(val value: String) : IntegrityTokenResult

    /** No token could be obtained; [retryable] says whether a later attempt may succeed. */
    data class Unavailable(val retryable: Boolean, val errorCode: Int?) : IntegrityTokenResult
}

/** Source of the Play Integrity token each Worker generation request carries. */
interface IntegrityTokenProvider {
    /** Prepares the provider ahead of need; safe to call repeatedly, never throws. */
    suspend fun warmUp()

    /** A token bound to [requestHash], or why there is none. Never throws. */
    suspend fun token(requestHash: String): IntegrityTokenResult
}
