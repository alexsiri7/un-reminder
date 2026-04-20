package net.interstellarai.unreminder.service.llm

/**
 * Model-download configuration helpers.
 *
 * Historically the on-device LLM URL lived exclusively in
 * `BuildConfig.MODEL_CDN_URL` (a CI-wired secret). That single URL has been
 * superseded by the per-descriptor [ModelCatalog] mechanism, but a handful
 * of catalog entries still ship with `https://placeholder.invalid/...`
 * sentinel URLs — either because we haven't self-hosted a gated model yet,
 * or because CI hasn't been given the real URL. Callers use
 * [isPlaceholderUrl] to detect that state and surface `AiStatus.Unavailable`
 * instead of attempting a download that can never succeed.
 */
object ModelConfig {
    /** Matches the fallback in `app/build.gradle.kts` when `MODEL_CDN_URL` is unset. */
    const val PLACEHOLDER_URL = "https://placeholder.invalid/model.task"

    /** Host used for all sentinel URLs we treat as "not really downloadable". */
    private const val PLACEHOLDER_HOST_PREFIX = "https://placeholder.invalid/"

    /**
     * @return true when [url] is blank or points at the `placeholder.invalid`
     * host we reserve for unresolved catalog entries (including the legacy
     * build-time placeholder).
     */
    fun isPlaceholderUrl(url: String?): Boolean =
        url.isNullOrBlank() || url.startsWith(PLACEHOLDER_HOST_PREFIX)
}
