package net.interstellarai.unreminder.domain.model

/**
 * `capScope` of the Worker's 402 body — whose budget ran out. Wire literals are pinned in
 * worker/test/fixtures/spend-wire.txt.
 */
enum class SpendCapScope {
    USER,
    GLOBAL;

    companion object {
        /** Null for anything the Worker did not say or this build does not know. */
        fun fromWire(raw: String): SpendCapScope? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

/** `capType` of the same body — how long until the budget refills. */
enum class SpendCapType {
    DAILY,
    MONTHLY;

    companion object {
        /** Null for anything the Worker did not say or this build does not know. */
        fun fromWire(raw: String): SpendCapType? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}
