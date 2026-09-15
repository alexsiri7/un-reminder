package net.interstellarai.unreminder.service.worker

/**
 * The Worker's 403: the per-user token was accepted but the Play Integrity gate was not
 * passed. [retryable] is true only when no token was attached because the local attempt
 * failed transiently, so a later run may pass.
 */
class WorkerIntegrityException(val reason: String, val retryable: Boolean) :
    Exception("Worker returned 403 — Play Integrity check failed ($reason)")
