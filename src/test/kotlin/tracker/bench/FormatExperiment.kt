package tracker.bench

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import tracker.concurrent.CancellationToken
import tracker.concurrent.ProgressListener
import tracker.io.FileFormat
import tracker.io.TaskFiles
import tracker.model.Task
import tracker.service.TaskGenerator
import tracker.store.ArrayBuffer

private const val TASK_COUNT = 10_000
private const val RUNS = 5
private const val WARMUP_RUNS = 3
private const val TEXT_FRAGMENT_LINES = 12
private const val BINARY_FRAGMENT_BYTES = 160
private const val BYTES_PER_ROW = 16
private const val BYTES_IN_KB = 1024.0
private const val NANOS_IN_MS = 1_000_000.0
private const val FIRST_PRINTABLE = 0x20
private const val LAST_PRINTABLE = 0x7E
private const val BYTE_MASK = 0xFF

fun main(args: Array<String>) {
    val directory = Path.of(args.firstOrNull() ?: "build/reports/formats")
    Files.createDirectories(directory)
    val out = ExperimentOutput(directory.resolve("formats.txt"))
    val tasks = generate()
    val files = TaskFiles()

    out.line("Задач: $TASK_COUNT. Время — среднее по $RUNS прогонам после $WARMUP_RUNS прогревочных, в миллисекундах")
    out.line("| Формат | Размер, КБ | Байт на задачу | Сохранение, мс | Загрузка, мс |")
    out.line("|---|---|---|---|---|")
    for (format in FileFormat.entries) {
        val file = directory.resolve("tasks.${format.extension}")
        repeat(WARMUP_RUNS) {
            files.write(file, tasks, ProgressListener.NONE)
            check(read(files, file) == TASK_COUNT)
        }
        val saveMillis = averageMillis { files.write(file, tasks, ProgressListener.NONE) }
        val loadMillis = averageMillis { check(read(files, file) == TASK_COUNT) }
        val size = Files.size(file)
        out.line(
            "| ${format.title} | ${"%.1f".format(size / BYTES_IN_KB)} | ${size / TASK_COUNT} | " +
                "${"%.2f".format(saveMillis)} | ${"%.2f".format(loadMillis)} |",
        )
    }
    for (format in FileFormat.entries) {
        val file = directory.resolve("tasks.${format.extension}")
        out.line()
        out.line("Фрагмент файла ${file.fileName} (${format.title}):")
        fragment(format, file).forEach(out::line)
    }
    out.save()
}

private fun generate(): ArrayBuffer<Task> {
    val generator = TaskGenerator(Random(Benchmark.DEFAULT_SEED), Benchmark.BASE_DATE)
    val tasks = ArrayBuffer<Task>(TASK_COUNT)
    for (id in 1..TASK_COUNT) tasks.add(generator.next(id))
    return tasks
}

private fun read(files: TaskFiles, file: Path): Int =
    files.read(file, CancellationToken(), ProgressListener.NONE) { true }.loaded

private inline fun averageMillis(action: () -> Unit): Double {
    var total = 0L
    repeat(RUNS) {
        val start = System.nanoTime()
        action()
        total += System.nanoTime() - start
    }
    return total / RUNS / NANOS_IN_MS
}

private fun fragment(format: FileFormat, file: Path): List<String> = when (format) {
    FileFormat.CSV, FileFormat.JSON -> Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
        List(TEXT_FRAGMENT_LINES) { reader.readLine() ?: "" }
    }
    FileFormat.BINARY -> {
        val bytes = Files.newInputStream(file).use { it.readNBytes(BINARY_FRAGMENT_BYTES) }
        bytes.indices.step(BYTES_PER_ROW).map { start ->
            val row = bytes.copyOfRange(start, minOf(start + BYTES_PER_ROW, bytes.size))
            val hex = row.joinToString(" ") { "%02X".format(it.toInt() and BYTE_MASK) }
            val text = row.joinToString("") { byte ->
                val code = byte.toInt() and BYTE_MASK
                if (code in FIRST_PRINTABLE..LAST_PRINTABLE) code.toChar().toString() else "."
            }
            "%04X  %-47s  %s".format(start, hex, text)
        }
    }
}
