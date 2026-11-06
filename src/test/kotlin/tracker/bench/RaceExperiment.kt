package tracker.bench

import java.nio.file.Path

fun main(args: Array<String>) {
    val out = ExperimentOutput(Path.of(args.firstOrNull() ?: "build/reports/race/race.txt"))
    val demo = RaceDemo()
    out.line("Потоков: ${RaceDemo.DEFAULT_THREADS}, вставок на поток: ${RaceDemo.DEFAULT_PER_THREAD}, " +
        "ядер процессора: ${Runtime.getRuntime().availableProcessors()}")
    out.line("| Режим | Ожидалось | Узлов в дереве | Счётчик size | Порядок ключей | Свойство кучи | Итог |")
    out.line("|---|---|---|---|---|---|---|")
    for (synchronized in booleanArrayOf(false, true)) {
        val result = demo.run(synchronized)
        val report = result.report
        val correct = report.isValid && report.nodesFound == result.expected && result.failedThreads == 0
        val mode = if (synchronized) "с synchronized" else "без защиты"
        val nodes = if (report.complete) report.nodesFound.toString() else "${report.nodesFound}+ (цикл)"
        val verdict = when {
            correct -> "дерево корректно"
            !report.complete -> "дерево повреждено: ссылки зациклились"
            else -> "дерево повреждено, потеряно узлов: ${maxOf(result.lost, 0)}"
        }
        out.line("| $mode | ${result.expected} | $nodes | ${report.sizeCounter} | ${yesNo(report.keysOrdered)} | " +
            "${yesNo(report.heapOrdered)} | $verdict |")
        result.firstError?.let { out.line("  первая ошибка потока: $it") }
    }
    out.save()
}

private fun yesNo(value: Boolean): String = if (value) "да" else "нет"
