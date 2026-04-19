package net.interstellarai.unreminder.service.llm

/**
 * Static description of an on-device LLM the user can pick in Settings.
 *
 * Each descriptor fully parameterises the download + integrity-check path in
 * [net.interstellarai.unreminder.worker.ModelDownloadWorker] and the engine-
 * init path in [PromptGeneratorImpl]: swapping to a different model is purely
 * a matter of writing a new [ModelDescriptor.id] to the active-model DataStore
 * and re-enqueuing the worker.
 *
 * Why a data class + a tiny registry instead of a more elaborate plugin
 * mechanism: there are two models today and adding a third is a ~6-line PR
 * against [ModelCatalog.all]. Nothing about this design blocks a future
 * runtime-loaded catalog (e.g. fetched from R2) — callers already resolve via
 * [ModelCatalog.byId] so we can replace the registry without touching them.
 */
data class ModelDescriptor(
    /**
     * Stable key persisted in DataStore. Must never change after a model has
     * shipped, or users who selected it will silently fall back to the
     * default on next app start.
     */
    val id: String,
    /** UI label. */
    val displayName: String,
    /** Short explanation shown under the display name. */
    val description: String,
    /**
     * HTTPS download URL. Must either serve the full model body or 416 on an
     * unsatisfiable `Range:` request — the worker uses RFC 7233 semantics for
     * resume-from-partial.
     */
    val url: String,
    /**
     * On-disk filename under `filesDir`. MUST be distinct per model so the
     * user can keep multiple downloaded simultaneously (or so we can fall
     * back between them without re-downloading). Never reuse filenames
     * between models — the integrity-check magic differs and a stale file
     * would get rejected and re-downloaded on every app start.
     */
    val fileName: String,
    /**
     * Expected total download size in bytes. Shown in the UI as a human-
     * readable figure; not used for validation (the worker trusts
     * Content-Length from the CDN instead).
     */
    val sizeBytes: Long,
    /**
     * Expected magic-byte prefix at offset 0 of the downloaded file. The
     * worker checks this after the stream completes and re-fetches if it
     * doesn't match (catches HTML error bodies, truncated downloads, etc.).
     */
    val magicPrefix: ByteArray,
    /**
     * Soft filter hint for the UI. Not enforced — low-RAM devices can still
     * select a large model, they'll just hit OOM on engine init and see
     * [AiStatus.Failed].
     */
    val minDeviceMemoryGb: Int,
    /**
     * Optional caveats surfaced to the user ("experimental", "gated on HF",
     * "known-broken on Pixel 8"). Null when the model is boring.
     */
    val notes: String? = null,
) {
    /**
     * `ByteArray` needs custom equals/hashCode so unit tests and set-like
     * operations on catalog entries behave sensibly. Auto-generated
     * data-class equals uses reference equality for array members.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModelDescriptor) return false
        return id == other.id &&
            displayName == other.displayName &&
            description == other.description &&
            url == other.url &&
            fileName == other.fileName &&
            sizeBytes == other.sizeBytes &&
            magicPrefix.contentEquals(other.magicPrefix) &&
            minDeviceMemoryGb == other.minDeviceMemoryGb &&
            notes == other.notes
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + magicPrefix.contentHashCode()
        result = 31 * result + minDeviceMemoryGb
        result = 31 * result + (notes?.hashCode() ?: 0)
        return result
    }
}

/**
 * Registry of every model the app is willing to load. Add new entries here
 * and they automatically show up in the Settings selector; no other wiring
 * needed. Keep the list short — each entry is a promise to the user that the
 * download + load path actually works.
 */
object ModelCatalog {
    /**
     * Latest Google Gemma 4 E2B model in LiteRT-LM container format.
     * Flagship default for capable devices.
     */
    val gemma4E2BLitertlm = ModelDescriptor(
        id = "gemma-4-e2b-it-litertlm",
        displayName = "Gemma 4 E2B (LiteRT-LM)",
        description = "Latest Google Gemma 4 model, 2B parameters, multimodal. 2.58 GB download.",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        fileName = "gemma-4-e2b-it.litertlm",
        sizeBytes = 2_583_085_056L,
        magicPrefix = byteArrayOf(0x4C, 0x49, 0x54, 0x45, 0x52, 0x54, 0x4C, 0x4D),
        minDeviceMemoryGb = 8,
        notes = "Experimental. Some devices can't load .litertlm files yet.",
    )

    /**
     * Smaller, proven-working Gemma 3 1B in MediaPipe `.task` format. Fallback
     * for low-RAM devices or when Gemma 4 fails to load.
     *
     * NB: The HuggingFace copy of this file is currently GATED, so the URL
     * below is a deliberate placeholder that the worker will short-circuit on.
     * Users wanting this model must self-host (or we do) and ship a real URL
     * in a follow-up PR.
     */
    val gemma3_1B_Task = ModelDescriptor(
        id = "gemma-3-1b-it-task",
        displayName = "Gemma 3 1B (Task)",
        description = "Smaller, known-good Gemma 3 model. 700 MB download, text-only.",
        url = "https://placeholder.invalid/gemma3-1b-it-int4.task",
        fileName = "gemma3-1b-it-int4.task",
        sizeBytes = 689_000_000L,
        magicPrefix = byteArrayOf(0x50, 0x4B, 0x03, 0x04),
        minDeviceMemoryGb = 4,
        notes = "Fallback option. File is gated on HuggingFace — you must self-host or provide your own URL.",
    )

    /** All models currently in the catalog, in display order. */
    val all: List<ModelDescriptor> = listOf(gemma4E2BLitertlm, gemma3_1B_Task)

    /**
     * Resolve a descriptor by its stable [ModelDescriptor.id]. Returns null
     * when the id refers to a model we've since removed — callers treat this
     * as "fall back to [default]".
     */
    fun byId(id: String): ModelDescriptor? = all.firstOrNull { it.id == id }

    /** Default on a fresh install. */
    val default: ModelDescriptor = gemma4E2BLitertlm
}
