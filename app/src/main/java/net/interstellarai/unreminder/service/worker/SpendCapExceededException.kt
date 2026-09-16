package net.interstellarai.unreminder.service.worker

import net.interstellarai.unreminder.domain.model.SpendCapScope
import net.interstellarai.unreminder.domain.model.SpendCapType

/** A null [capScope] or [capType] means the Worker did not say (older Worker, non-JSON body). */
class SpendCapExceededException(
    val capScope: SpendCapScope? = null,
    val capType: SpendCapType? = null,
) : Exception("Worker spend cap reached")
