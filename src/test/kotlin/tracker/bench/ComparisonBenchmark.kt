package tracker.bench

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    val directory = Path.of(args.firstOrNull() ?: "build/reports/benchmark")
    val out = ExperimentOutput(directory.resolve("benchmark.txt"))
    out.line("Замеры: среднее из ${Benchmark.REPEATS} прогонов после прогрева, время в миллисекундах")
    val report = Benchmark(arrayOf(TreapTarget.SPEC, TreeMapTarget.SPEC)).run()
    val csv = mutableListOf("operation;n;" + report.targetNames.joinToString(";") { "$it, ms" })
    out.line("| Операция | n | " + report.targetNames.joinToString(" | ") { "$it, мс" } + " | Дерево / TreeMap |")
    out.line("|---|---|" + report.targetNames.joinToString("") { "---|" } + "---|")
    report.rows.forEach { row ->
        val ratio = row.millis[0] / row.millis[1]
        val times = row.millis.joinToString(" | ") { "%.2f".format(it) }
        out.line("| ${row.operation.title} | ${row.size} | $times | ${"%.2f".format(ratio)} |")
        csv.add("${row.operation.name};${row.size};" + row.millis.joinToString(";") { "%.3f".format(it) })
    }
    report.sizes.forEachIndexed { index, size -> out.line("Высота декартова дерева при n = $size: ${report.heights[index]}") }
    out.save()
    val csvFile = directory.resolve("comparison.csv")
    Files.write(csvFile, csv, StandardCharsets.UTF_8)
    println("CSV для графика: ${csvFile.toAbsolutePath()}")
}
