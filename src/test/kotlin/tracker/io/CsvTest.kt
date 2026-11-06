package tracker.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import tracker.concurrent.CancellationToken
import tracker.concurrent.OperationCancelledException
import tracker.concurrent.ProgressListener
import tracker.items
import tracker.model.Task
import tracker.store.ArrayBuffer

class CsvTest {
    private val valid = "REGULAR;1;Задача;Иванов;2026-10-10;5;"

    private fun file(vararg lines: String): Path {
        val path = Files.createTempFile("tasks", ".csv")
        Files.write(path, lines.toList())
        path.toFile().deleteOnExit()
        return path
    }

    private fun load(path: Path, token: CancellationToken = CancellationToken(), progress: ProgressListener = ProgressListener.NONE): Pair<List<Task>, LoadResult> {
        val tasks = mutableListOf<Task>()
        val ids = mutableSetOf<Int>()
        val result = CsvLoader().load(path, token, progress) { task -> ids.add(task.id).also { added -> if (added) tasks.add(task) } }
        return tasks to result
    }

    private fun singleSkippedCode(brokenLine: String): DataErrorCode {
        val (_, result) = load(file(TaskCsvFormat.HEADER, brokenLine))
        return result.skipped.items().single().code
    }

    @Test
    fun validLineIsLoaded() {
        val (tasks, result) = load(file(TaskCsvFormat.HEADER, valid))
        assertEquals(listOf(1), tasks.map { it.id })
        assertEquals(1, result.loaded)
    }

    @Test
    fun wrongFieldCountIsReported() {
        assertEquals(DataErrorCode.WRONG_FIELD_COUNT, singleSkippedCode("REGULAR;2;Мало полей;Иванов"))
    }

    @Test
    fun badNumberIsReported() {
        assertEquals(DataErrorCode.BAD_NUMBER, singleSkippedCode("REGULAR;3;Не число;Иванов;2026-10-10;много;"))
    }

    @Test
    fun badDateIsReported() {
        assertEquals(DataErrorCode.BAD_DATE, singleSkippedCode("REGULAR;4;Дата;Иванов;10.10.2026;5;"))
    }

    @Test
    fun importanceOutOfRangeIsReported() {
        assertEquals(DataErrorCode.OUT_OF_RANGE, singleSkippedCode("REGULAR;5;Диапазон;Иванов;2026-10-10;42;"))
    }

    @Test
    fun unknownTypeIsReported() {
        assertEquals(DataErrorCode.UNKNOWN_TYPE, singleSkippedCode("TODO;6;Тип;Иванов;2026-10-10;5;"))
    }

    @Test
    fun urgentWithoutDutyPersonIsReported() {
        assertEquals(DataErrorCode.INVALID_FIELD, singleSkippedCode("URGENT;7;Без дежурного;Иванов;2026-10-10;5;"))
    }

    @Test
    fun duplicateIdIsReported() {
        val (_, result) = load(file(TaskCsvFormat.HEADER, valid, valid))
        assertEquals(DataErrorCode.DUPLICATE_ID, result.skipped.items().single().code)
    }

    @Test
    fun skippedLineKeepsItsNumber() {
        val (_, result) = load(file(TaskCsvFormat.HEADER, valid, "REGULAR;2;Мало полей"))
        assertEquals("строка 3", result.skipped.items().single().position)
    }

    @Test
    fun badHeaderStopsLoading() {
        val error = assertFailsWith<DataFileException> { load(file("id;title", "1;Задача")) }
        assertEquals(DataErrorCode.BAD_HEADER, error.code)
    }

    @Test
    fun emptyFileIsReported() {
        val error = assertFailsWith<DataFileException> { load(file()) }
        assertEquals(DataErrorCode.EMPTY_FILE, error.code)
    }

    @Test
    fun missingFileIsReported() {
        val error = assertFailsWith<DataFileException> { load(Path.of("missing_tasks_file.csv")) }
        assertEquals(DataErrorCode.FILE_NOT_FOUND, error.code)
    }

    @Test
    fun savedFileLoadsBack() {
        val source = file(
            TaskCsvFormat.HEADER,
            "URGENT;2;Сервер;Иванов;2026-10-03;10;Петров",
            "ARCHIVED;4;Старое;Петрова;2026-09-01;3;",
            "REGULAR;1;Отчёт;Сидоров;2026-10-10;5;",
        )
        val (tasks, _) = load(source)
        val target = Files.createTempFile("saved", ".csv")
        target.toFile().deleteOnExit()
        val buffer = ArrayBuffer<Task>()
        tasks.forEach(buffer::add)
        CsvWriter().save(target, buffer)
        assertEquals(Files.readAllLines(source), Files.readAllLines(target))
    }

    @Test
    fun progressReachesTotal() {
        val lines = listOf(TaskCsvFormat.HEADER) + (1..5_000).map { "REGULAR;$it;Задача;Иванов;2026-10-10;5;" }
        var last = -1
        var total = 0
        load(file(*lines.toTypedArray()), progress = ProgressListener { _, done, all -> last = done; total = all })
        assertEquals(total, last)
    }

    @Test
    fun cancelledLoadThrows() {
        val token = CancellationToken()
        token.cancel()
        assertFailsWith<OperationCancelledException> { load(file(TaskCsvFormat.HEADER, valid), token) }
        assertTrue(token.isCancelled)
    }
}
