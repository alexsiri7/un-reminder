package net.interstellarai.unreminder.service.worker

/** The per-user Worker token a user pastes into Cloud AI settings. */
object WorkerToken {
    /** Mirrors `TOKEN_PATTERN` in worker/src/lib/tokens.ts — keep in sync. */
    val PATTERN = Regex("^ur1_[0-9a-f]{16}_[0-9a-f]{64}$")

    fun isWellFormed(raw: String): Boolean = PATTERN.matches(raw)

    /** The non-secret `ur1_<id>` prefix, safe to show on screen and quote to Alex. */
    fun displayId(token: String): String = token.take(20)
}
