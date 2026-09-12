package net.interstellarai.unreminder.domain.model

/**
 * Structural form of a generated notification, assigned by the worker at generation time.
 * Selection rotates shapes so consecutive nudges for a habit differ in kind, not only in words.
 */
enum class VariantShape {
    QUESTION,
    STATEMENT,
    CHALLENGE,
    OBSERVATION,
    TERSE,
    TIMEBOXED,
}
