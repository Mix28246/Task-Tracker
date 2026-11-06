package tracker.bench

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ExperimentOutput(private val file: Path) {
    private val lines = mutableListOf<String>()

    fun line(text: String = "") {
        println(text)
        lines.add(text)
    }

    fun save() {
        Files.createDirectories(file.toAbsolutePath().parent)
        Files.write(file, lines, StandardCharsets.UTF_8)
        println("Результат сохранён в файл: ${file.toAbsolutePath()}")
    }
}
