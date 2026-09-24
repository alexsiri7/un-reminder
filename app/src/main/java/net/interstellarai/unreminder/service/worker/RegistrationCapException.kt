package net.interstellarai.unreminder.service.worker

/** The Worker's 429 for its service-wide daily cap on new registrations. */
class RegistrationCapException : Exception("Worker's daily registration limit reached")
