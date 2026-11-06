package tracker.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import tracker.Fixtures
import tracker.concurrent.CancellationToken
import tracker.concurrent.OperationCancelledException
import tracker.concurrent.ProgressListener
import tracker.items
import tracker.model.Task
import tracker.store.ArrayBuffer

class TaskFilesTest {
    private val files = TaskFiles()

    private fun read(file: Path): Pair<List<Task>, LoadResult> {
        val tasks = ArrayBuffer<Task>()
        val ids = HashSet<Int>()
        val result = files.read(file, CancellationToken(), ProgressListener.NONE) { task ->
            ids.add(task.id).also { added -> if (added) tasks.add(task) }
        }
        return tasks.items() to result
    }

    private fun records(file: Path): List<TaskRecord> = read(file).first.map(TaskRecord::of).sortedBy { it.id }

    @Test
    fun jsonFixtureHasSameTasksAsCsvFixture() {
        assertEquals(records(Fixtures.path("tasks.csv")), records(Fixtures.path("tasks.json")))
    }

    @Test
    fun binaryFixtureHasSameTasksAsCsvFixture() {
        assertEquals(records(Fixtures.path("tasks.csv")), records(Fixtures.path("tasks.bin")))
    }

    private fun writeAndReadBack(format: FileFormat): List<TaskRecord> {
        val tasks = ArrayBuffer<Task>()
        read(Fixtures.path("tasks.csv")).first.forEach(tasks::add)
        val file = Files.createTempFile("export", ".${format.extension}")
        file.toFile().deleteOnExit()
        files.write(file, tasks, ProgressListener.NONE)
        return records(file)
    }

    @Test
    fun csvExportReadsBack() {
        assertEquals(records(Fixtures.path("tasks.csv")), writeAndReadBack(FileFormat.CSV))
    }

    @Test
    fun jsonExportReadsBack() {
        assertEquals(records(Fixtures.path("tasks.csv")), writeAndReadBack(FileFormat.JSON))
    }

    @Test
    fun binaryExportReadsBack() {
        assertEquals(records(Fixtures.path("tasks.csv")), writeAndReadBack(FileFormat.BINARY))
    }

    @Test
    fun invalidRecordsAreSkipped() {
        val (_, result) = read(Fixtures.path("bad_records.json"))
        assertEquals(1, result.loaded)
    }

    @Test
    fun skippedRecordsKeepPositionAndCode() {
        val (_, result) = read(Fixtures.path("bad_records.json"))
        assertEquals(
            listOf(
                "запись 2" to DataErrorCode.OUT_OF_RANGE,
                "запись 3" to DataErrorCode.INVALID_FIELD,
                "запись 4" to DataErrorCode.DUPLICATE_ID,
            ),
            result.skipped.items().map { it.position to it.code },
        )
    }

    @Test
    fun brokenCsvFixtureSkipsBadLines() {
        val (_, result) = read(Fixtures.path("broken.csv"))
        assertEquals(3 to 8, result.loaded to result.skipped.size)
    }

    @Test
    fun unknownExtensionIsRejected() {
        val error = assertFailsWith<DataFileException> { read(Path.of("tasks.xml")) }
        assertEquals(DataErrorCode.UNKNOWN_FORMAT, error.code)
    }

    @Test
    fun cancelledImportStopsReading() {
        val token = CancellationToken()
        token.cancel()
        assertFailsWith<OperationCancelledException> {
            files.read(Fixtures.path("tasks.json"), token, ProgressListener.NONE) { true }
        }
    }
}
