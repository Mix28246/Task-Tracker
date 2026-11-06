package tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import tracker.concurrent.CancellationToken
import tracker.concurrent.ProgressListener

data class JobProgress(val title: String, val stage: String, val percent: Int, val cancellable: Boolean)

class BackgroundRunner {
    var progress: JobProgress? by mutableStateOf(null)
        private set

    private var token: CancellationToken? = null

    val isBusy: Boolean
        get() = progress != null

    fun <R> start(
        title: String,
        cancellable: Boolean = true,
        work: (CancellationToken, ProgressListener) -> R,
        onFinished: (Result<R>) -> Unit,
    ) {
        if (isBusy) return
        val job = CancellationToken()
        token = job
        progress = JobProgress(title, "", 0, cancellable)
        val listener = PercentListener(title, cancellable) { update -> onUiThread { if (token === job) progress = update } }
        thread(isDaemon = true, name = "tracker-background") {
            val result = runCatching { work(job, listener) }
            onUiThread {
                token = null
                progress = null
                onFinished(result)
            }
        }
    }

    fun cancel() {
        token?.cancel()
    }

    private class PercentListener(
        private val title: String,
        private val cancellable: Boolean,
        private val publish: (JobProgress) -> Unit,
    ) : ProgressListener {
        private var lastStage = ""
        private var lastPercent = -1

        override fun onProgress(stage: String, done: Int, total: Int) {
            val percent = if (total <= 0) FULL else (done.toLong() * FULL / total).toInt()
            if (stage == lastStage && percent == lastPercent) return
            lastStage = stage
            lastPercent = percent
            publish(JobProgress(title, stage, percent, cancellable))
        }
    }

    private companion object {
        const val FULL = 100

        fun onUiThread(action: () -> Unit) {
            SwingUtilities.invokeLater { action() }
        }
    }
}
