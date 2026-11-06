package tracker.bench

import java.time.LocalDate
import kotlin.random.Random
import tracker.model.Task
import tracker.model.TaskKey
import tracker.model.key
import tracker.service.TaskGenerator
import tracker.store.ArrayBuffer
import tracker.store.TreapStore

enum class BenchOperation(val title: String) {
    INSERT("Вставка (n раз)"),
    FIND("Поиск по ключу (n раз)"),
    RANGE("Задачи в интервале сроков (${Benchmark.QUERIES} запросов)"),
    TOP("${Benchmark.TOP_N} самых важных (${Benchmark.QUERIES} запросов)"),
    REMOVE("Удаление (n раз)"),
}

interface BenchTarget {
    fun insertAll(tasks: Array<Task>)

    fun findAll(keys: Array<TaskKey>): Long

    fun rangeCount(from: TaskKey, to: TaskKey): Int

    fun topCount(n: Int): Int

    fun removeAll(keys: Array<TaskKey>): Long

    fun height(): Int

    fun isEmpty(): Boolean
}

class BenchTargetSpec(val name: String, val create: () -> BenchTarget)

class TreapTarget : BenchTarget {
    private val treap = TreapStore<TaskKey, Task>(keyOf = { it.key }, priorityOf = { it.importance })

    override fun insertAll(tasks: Array<Task>) {
        for (task in tasks) treap.add(task)
    }

    override fun findAll(keys: Array<TaskKey>): Long {
        var sum = 0L
        for (key in keys) sum += treap.findByKey(key)?.id ?: 0
        return sum
    }

    override fun rangeCount(from: TaskKey, to: TaskKey): Int = treap.range(from, to).size

    override fun topCount(n: Int): Int = treap.top(n).size

    override fun removeAll(keys: Array<TaskKey>): Long {
        var removed = 0L
        for (key in keys) if (treap.removeByKey(key)) removed++
        return removed
    }

    override fun height(): Int = treap.height()

    override fun isEmpty(): Boolean = treap.size == 0

    companion object {
        val SPEC = BenchTargetSpec("Декартово дерево") { TreapTarget() }
    }
}

class BenchWorkload(val tasks: Array<Task>, val keys: Array<TaskKey>, val windowStarts: Array<LocalDate>) {
    companion object {
        fun create(size: Int, seed: Int): BenchWorkload {
            val random = Random(seed)
            val generator = TaskGenerator(random, Benchmark.BASE_DATE)
            val tasks = Array<Task>(size) { index -> generator.next(index + 1) }
            val keys = Array(size) { index -> tasks[index].key }
            for (index in size - 1 downTo 1) {
                val other = random.nextInt(index + 1)
                val tmp = keys[index]
                keys[index] = keys[other]
                keys[other] = tmp
            }
            val windowStarts = Array(Benchmark.QUERIES) { tasks[random.nextInt(size)].deadline }
            return BenchWorkload(tasks, keys, windowStarts)
        }
    }
}

class BenchRow(val operation: BenchOperation, val size: Int, val millis: DoubleArray)

class BenchReport(
    val targetNames: Array<String>,
    val rows: ArrayBuffer<BenchRow>,
    val sizes: IntArray,
    val heights: IntArray,
)

class Benchmark(
    private val targets: Array<BenchTargetSpec> = arrayOf(TreapTarget.SPEC),
    private val seed: Int = DEFAULT_SEED,
) {
    fun run(sizes: IntArray = DEFAULT_SIZES): BenchReport {
        val warmup = BenchWorkload.create(WARMUP_SIZE, seed)
        for (spec in targets) measure(spec.create(), warmup)

        val rows = ArrayBuffer<BenchRow>()
        val heights = IntArray(sizes.size)
        val operations = BenchOperation.entries.size
        sizes.forEachIndexed { sizeIndex, size ->
            val sums = Array(operations) { DoubleArray(targets.size) }
            repeat(REPEATS) { attempt ->
                val workload = BenchWorkload.create(size, seed + attempt + 1)
                targets.forEachIndexed { targetIndex, spec ->
                    val target = spec.create()
                    val measurement = measure(target, workload)
                    for (operation in 0 until operations) {
                        sums[operation][targetIndex] += measurement.nanos[operation].toDouble()
                    }
                    if (targetIndex == 0) heights[sizeIndex] = measurement.height
                }
            }
            BenchOperation.entries.forEach { operation ->
                val millis = DoubleArray(targets.size) { sums[operation.ordinal][it] / REPEATS / NANOS_IN_MS }
                rows.add(BenchRow(operation, size, millis))
            }
        }
        return BenchReport(Array(targets.size) { targets[it].name }, rows, sizes, heights)
    }

    private class Measurement(val nanos: LongArray, val height: Int)

    private fun measure(target: BenchTarget, workload: BenchWorkload): Measurement {
        val nanos = LongArray(BenchOperation.entries.size)
        var sink = 0L

        nanos[BenchOperation.INSERT.ordinal] = timed { target.insertAll(workload.tasks) }
        val height = target.height()

        nanos[BenchOperation.FIND.ordinal] = timed { sink += target.findAll(workload.keys) }

        nanos[BenchOperation.RANGE.ordinal] = timed {
            for (from in workload.windowStarts) {
                sink += target.rangeCount(TaskKey.firstOf(from), TaskKey.lastOf(from.plusDays(WINDOW_DAYS)))
            }
        }

        nanos[BenchOperation.TOP.ordinal] = timed { repeat(QUERIES) { sink += target.topCount(TOP_N) } }

        nanos[BenchOperation.REMOVE.ordinal] = timed { sink += target.removeAll(workload.keys) }

        check(sink != Long.MIN_VALUE)
        check(target.isEmpty())
        return Measurement(nanos, height)
    }

    private inline fun timed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    companion object {
        const val QUERIES = 1_000
        const val TOP_N = 10
        const val REPEATS = 3
        const val WARMUP_SIZE = 10_000
        const val WINDOW_DAYS = 30L
        const val DEFAULT_SEED = 42
        private const val NANOS_IN_MS = 1_000_000.0
        val BASE_DATE: LocalDate = LocalDate.of(2026, 1, 1)
        val DEFAULT_SIZES = intArrayOf(10_000, 100_000)
    }
}
