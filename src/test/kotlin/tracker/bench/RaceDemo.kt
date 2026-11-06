package tracker.bench

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.random.Random
import tracker.model.Task
import tracker.model.TaskKey
import tracker.model.key
import tracker.service.TaskGenerator
import tracker.store.EntityStore
import tracker.store.StructureReport
import tracker.store.SynchronizedStore
import tracker.store.TreapStore

data class RaceResult(
    val synchronized: Boolean,
    val threads: Int,
    val perThread: Int,
    val report: StructureReport,
    val failedThreads: Int,
    val firstError: String?,
    val unfinishedThreads: Int,
    val millis: Long,
) {
    val expected: Int
        get() = threads * perThread

    val lost: Int
        get() = expected - report.nodesFound
}

class RaceDemo(private val seed: Int = Benchmark.DEFAULT_SEED) {
    fun run(synchronized: Boolean, threads: Int = DEFAULT_THREADS, perThread: Int = DEFAULT_PER_THREAD): RaceResult {
        val treap = TreapStore<TaskKey, Task>(keyOf = { it.key }, priorityOf = { it.importance })
        val store: EntityStore<TaskKey, Task> = if (synchronized) SynchronizedStore(treap) else treap
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val failed = AtomicInteger()
        val firstError = AtomicReference<Throwable?>(null)
        val workers = Array(threads) { worker ->
            thread(isDaemon = true, name = "race-$worker") {
                try {
                    val generator = TaskGenerator(Random(seed + worker), Benchmark.BASE_DATE)
                    val tasks = Array<Task>(perThread) { index -> generator.next(index * threads + worker + 1) }
                    ready.countDown()
                    start.await()
                    for (task in tasks) store.add(task)
                } catch (e: Throwable) {
                    ready.countDown()
                    failed.incrementAndGet()
                    firstError.compareAndSet(null, e)
                }
            }
        }
        ready.await()
        val begin = System.nanoTime()
        start.countDown()
        val deadline = System.currentTimeMillis() + JOIN_TIMEOUT_MS
        for (worker in workers) worker.join((deadline - System.currentTimeMillis()).coerceAtLeast(1))
        val millis = (System.nanoTime() - begin) / NANOS_IN_MS
        val unfinished = workers.count { it.isAlive }
        val report = treap.inspect(limit = threads * perThread * 2)
        val error = firstError.get()?.let { "${it::class.simpleName}: ${it.message}" }
        return RaceResult(synchronized, threads, perThread, report, failed.get(), error, unfinished, millis)
    }

    companion object {
        const val DEFAULT_THREADS = 4
        const val DEFAULT_PER_THREAD = 20_000
        private const val JOIN_TIMEOUT_MS = 10_000L
        private const val NANOS_IN_MS = 1_000_000L
    }
}
