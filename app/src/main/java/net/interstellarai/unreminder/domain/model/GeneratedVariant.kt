package net.interstellarai.unreminder.domain.model

/** One item of a `/v1/generate/batch` response: text plus the shape it was written in. */
data class GeneratedVariant(
    val text: String,
    val shape: VariantShape,
    val actionUrl: String?,
    val spriteTag: String?,
)
