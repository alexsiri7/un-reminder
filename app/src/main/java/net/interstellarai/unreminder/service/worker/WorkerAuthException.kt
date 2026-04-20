package net.interstellarai.unreminder.service.worker

class WorkerAuthException : Exception("Worker returned 401 — check secret in Settings")
