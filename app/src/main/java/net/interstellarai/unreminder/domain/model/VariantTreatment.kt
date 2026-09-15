package net.interstellarai.unreminder.domain.model

/**
 * The identity a variant's look derives from on every surface (#416): the notification style,
 * the widget layout and the reminder detail layout all index their own entries by the same
 * seed, so a variant is recognisably itself wherever it shows. Presentation is never a
 * Variation column: it costs no pool space, needs no generating and survives a regeneration.
 * The seed is the raw id, not a hash, so consecutively generated variants spread across every
 * surface's looks and [net.interstellarai.unreminder.data.db.VariationDao.getUnusedForHabit]
 * can rank candidates by the same value in SQL.
 */
object VariantTreatment {
    /** A level-description fallback row has no variation; the habit stands in for it. */
    fun seed(variationId: Long?, habitId: Long): Long = variationId ?: habitId

    /** `entries[seed mod size]`; `.mod()` (not `%`) keeps the index non-negative for a negative seed. */
    fun <T> pick(entries: List<T>, seed: Long): T = entries[seed.mod(entries.size)]
}
