package net.interstellarai.unreminder.domain.model

/**
 * A `/v1/generate/batch` response: the variants plus the generation version they were
 * produced under, which is [net.interstellarai.unreminder.data.db.VariationEntity.UNVERSIONED]
 * when the Worker predates versions.
 */
data class GeneratedBatch(
    val variants: List<GeneratedVariant>,
    val generationVersion: Int,
)
