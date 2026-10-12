package tracker.csv

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException
import tracker.model.ArchivedTask
import tracker.model.RegularTask
import tracker.model.Task
import tracker.model.TaskRules
import tracker.model.TaskType
import tracker.model.UrgentTask

object TaskCsvFormat {
    const val SEPARATOR = ";"
    const val HEADER = "type;id;title;assignee;deadline;importance;dutyPerson"
    const val FIELD_COUNT = 7

    private const val COL_TYPE = 0
    private const val COL_ID = 1
    private const val COL_TITLE = 2
    private const val COL_ASSIGNEE = 3
    private const val COL_DEADLINE = 4
    private const val COL_IMPORTANCE = 5
    private const val COL_DUTY_PERSON = 6

    fun parseLine(line: String, lineNumber: Int): Task {
        val parts = line.split(SEPARATOR).map { it.trim() }
        if (parts.size != FIELD_COUNT) {
            throw CsvLineException(
                CsvErrorCode.WRONG_FIELD_COUNT, lineNumber,
                "ожидалось $FIELD_COUNT, получено ${parts.size}",
            )
        }

        val type = TaskType.fromCsvCode(parts[COL_TYPE])
            ?: throw CsvLineException(CsvErrorCode.UNKNOWN_TYPE, lineNumber, "«${parts[COL_TYPE]}»")
        val id = parseInt(parts[COL_ID], "id", lineNumber)
        val title = parts[COL_TITLE]
        val assignee = parts[COL_ASSIGNEE]
        val deadline = parseDate(parts[COL_DEADLINE], lineNumber)
        val importance = parseInt(parts[COL_IMPORTANCE], "importance", lineNumber)
        val dutyPerson = parts[COL_DUTY_PERSON]

        checkFields(id, title, assignee, importance, lineNumber)

        return when (type) {
            TaskType.REGULAR -> RegularTask(id, title, assignee, deadline, importance)
            TaskType.ARCHIVED -> ArchivedTask(id, title, assignee, deadline, importance)
            TaskType.URGENT -> {
                if (dutyPerson.isEmpty()) {
                    throw CsvLineException(CsvErrorCode.INVALID_FIELD, lineNumber, "у срочной задачи не указан дежурный")
                }
                UrgentTask(id, title, assignee, deadline, importance, dutyPerson)
            }
        }
    }

    fun toLine(task: Task): String {
        val dutyPerson = when (task) {
            is UrgentTask -> task.dutyPerson
            is RegularTask, is ArchivedTask -> ""
        }
        return listOf(
            task.type.csvCode,
            task.id.toString(),
            task.title,
            task.assignee,
            task.deadline.toString(),
            task.importance.toString(),
            dutyPerson,
        ).joinToString(SEPARATOR)
    }

    private fun parseInt(value: String, field: String, lineNumber: Int): Int =
        value.toIntOrNull()
            ?: throw CsvLineException(CsvErrorCode.BAD_NUMBER, lineNumber, "$field = «$value»")

    private fun parseDate(value: String, lineNumber: Int): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw CsvLineException(CsvErrorCode.BAD_DATE, lineNumber, "«$value»")
        }

    private fun checkFields(id: Int, title: String, assignee: String, importance: Int, lineNumber: Int) {
        if (id <= 0) {
            throw CsvLineException(CsvErrorCode.OUT_OF_RANGE, lineNumber, "id = $id, должен быть > 0")
        }
        if (!TaskRules.isImportanceValid(importance)) {
            throw CsvLineException(
                CsvErrorCode.OUT_OF_RANGE, lineNumber,
                "importance = $importance, допустимо ${TaskRules.MIN_IMPORTANCE}..${TaskRules.MAX_IMPORTANCE}",
            )
        }
        val textErrors = TaskRules.validateText("Название", title) + TaskRules.validateText("Исполнитель", assignee)
        if (textErrors.isNotEmpty()) {
            throw CsvLineException(CsvErrorCode.INVALID_FIELD, lineNumber, textErrors.joinToString("; "))
        }
    }
}

data class LoadResult(
    val tasks: List<Task>,
    val skipped: List<CsvLineException>,
)

class CsvLoader {
    fun load(file: Path): LoadResult {
        val lines = readLines(file)
        if (lines.isEmpty()) throw CsvFileException(CsvErrorCode.EMPTY_FILE, file)

        val header = lines.first().removePrefix(UTF8_BOM).trim()
        if (header != TaskCsvFormat.HEADER) throw CsvFileException(CsvErrorCode.BAD_HEADER, file)

        val tasks = mutableListOf<Task>()
        val skipped = mutableListOf<CsvLineException>()
        val seenIds = mutableSetOf<Int>()

        lines.withIndex().drop(1).forEach { (index, line) ->
            if (line.isBlank()) return@forEach
            val lineNumber = index + 1
            try {
                val task = TaskCsvFormat.parseLine(line, lineNumber)
                if (!seenIds.add(task.id)) {
                    throw CsvLineException(CsvErrorCode.DUPLICATE_ID, lineNumber, "id = ${task.id}")
                }
                tasks.add(task)
            } catch (e: CsvLineException) {
                skipped.add(e)
            }
        }
        return LoadResult(tasks, skipped)
    }

    private fun readLines(file: Path): List<String> =
        try {
            Files.readAllLines(file, StandardCharsets.UTF_8)
        } catch (e: NoSuchFileException) {
            throw CsvFileException(CsvErrorCode.FILE_NOT_FOUND, file, e)
        } catch (e: IOException) {
            throw CsvFileException(CsvErrorCode.IO_ERROR, file, e)
        }

    private companion object {
        const val UTF8_BOM = "\uFEFF"
    }
}

class CsvWriter {
    fun save(tasks: List<Task>, file: Path) {
        val lines = listOf(TaskCsvFormat.HEADER) + tasks.map { TaskCsvFormat.toLine(it) }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8)
        } catch (e: IOException) {
            throw CsvFileException(CsvErrorCode.IO_ERROR, file, e)
        }
    }
}
