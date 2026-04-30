package net.interstellarai.unreminder.service.worker

class WorkerError(val code: Int, val body: String) :
    Exception("Worker error $code: $body") {
    fun isServerError(): Boolean = code in 500..599
}
