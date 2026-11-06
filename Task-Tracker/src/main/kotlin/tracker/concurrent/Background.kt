package tracker.concurrent

class CancellationToken {
    @Volatile
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }

    fun throwIfCancelled() {
        if (isCancelled) throw OperationCancelledException()
    }
}

class OperationCancelledException : RuntimeException("Операция отменена")

fun interface ProgressListener {
    fun onProgress(stage: String, done: Int, total: Int)

    companion object {
        val NONE = ProgressListener { _, _, _ -> }
    }
}
