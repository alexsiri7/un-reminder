package net.interstellarai.unreminder.service.llm

/**
 * Model-download configuration helpers.
 *
 * The on-device LLM model is downloaded on first launch from a CDN URL baked into
 * the APK at build time via `BuildConfig.MODEL_CDN_URL`. When CI is missing the
 * `MODEL_CDN_URL` secret, the build falls back to a placeholder host that will
 * never resolve — this helper lets callers detect that misconfiguration and fail
 * loudly instead of silently attempting a download that can never succeed.
 */
object ModelConfig {
    /** Matches the fallback in `app/build.gradle.kts` when `MODEL_CDN_URL` is unset. */
    const val PLACEHOLDER_URL = "https://placeholder.invalid/model.task"

    /**
     * @return true when [url] is blank or equals the build-time placeholder, indicating
     * that no real model URL was wired through CI and AI features cannot work.
     */
    fun isPlaceholderUrl(url: String?): Boolean =
        url.isNullOrBlank() || url == PLACEHOLDER_URL
}
