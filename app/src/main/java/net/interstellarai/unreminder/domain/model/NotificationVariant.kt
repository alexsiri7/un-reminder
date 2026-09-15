package net.interstellarai.unreminder.domain.model

data class NotificationVariant(
    val text: String,
    val actionUrl: String?,
    val spriteTag: String? = null,
    /** The pool row the text came from; null for a level-description fallback. */
    val variationId: Long? = null,
)
