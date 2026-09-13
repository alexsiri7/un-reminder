package net.interstellarai.unreminder.domain.model

/** One item of a `/v1/generate/batch` response: text plus the shape and modes it was written for. */
data class GeneratedVariant(
    val text: String,
    val shape: VariantShape,
    /** Empty when the text reads naturally in any mode. */
    val modes: Set<ActivityMode>,
    val actionUrl: String?,
    val spriteTag: String?,
)
