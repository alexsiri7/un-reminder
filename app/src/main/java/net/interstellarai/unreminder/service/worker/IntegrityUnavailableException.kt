package net.interstellarai.unreminder.service.worker

/**
 * Play Integrity gave this install no token to register with. Thrown before any request, so the
 * Worker is never asked; [retryable] says whether a later attempt may get one.
 */
class IntegrityUnavailableException(val retryable: Boolean) :
    Exception("Play Integrity token unavailable on this device")

/** This build has no Play Integrity Cloud project number, so it can never register. */
class IntegrityNotConfiguredException :
    Exception("This build was made without Play Integrity support")
