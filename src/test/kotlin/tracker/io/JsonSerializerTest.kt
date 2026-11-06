package tracker.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import tracker.Fixtures

class JsonSerializerTest {
    private val serializer = JsonTaskSerializer()

    private fun errorCode(file: Path): DataErrorCode = assertFailsWith<DataFileException> { serializer.load(file) }.code

    @Test
    fun roundTripReturnsSameRecords() {
        val original = serializer.load(Fixtures.path("tasks.json"))
        val file = Files.createTempFile("roundtrip", ".json")
        file.toFile().deleteOnExit()
        serializer.save(original, file)
        assertEquals(original.tasks.toList(), serializer.load(file).tasks.toList())
    }

    @Test
    fun referenceFileHasExpectedFirstRecord() {
        val first = serializer.load(Fixtures.path("tasks.json")).tasks.first()
        assertEquals(TaskRecord("ARCHIVED", 4, "Настроить репозиторий", "Иван Петров", "2026-09-01", 3, ""), first)
    }

    @Test
    fun savedFileStoresFormatVersion() {
        val file = Files.createTempFile("version", ".json")
        file.toFile().deleteOnExit()
        serializer.save(TaskArchive(TaskArchive.CURRENT_VERSION, emptyArray()), file)
        assertTrue(Files.readString(file).contains("\"version\" : ${TaskArchive.CURRENT_VERSION}"))
    }

    @Test
    fun brokenSyntaxIsReportedAsCorrupted() {
        assertEquals(DataErrorCode.CORRUPTED_FILE, errorCode(Fixtures.path("broken_syntax.json")))
    }

    @Test
    fun missingFieldIsReportedAsCorrupted() {
        assertEquals(DataErrorCode.CORRUPTED_FILE, errorCode(Fixtures.path("missing_field.json")))
    }

    @Test
    fun newerVersionIsRejected() {
        assertEquals(DataErrorCode.UNSUPPORTED_VERSION, errorCode(Fixtures.path("wrong_version.json")))
    }

    @Test
    fun emptyFileIsReported() {
        assertEquals(DataErrorCode.EMPTY_FILE, errorCode(Fixtures.path("empty.json")))
    }

    @Test
    fun missingFileIsReported() {
        assertEquals(DataErrorCode.FILE_NOT_FOUND, errorCode(Path.of("missing_tasks_file.json")))
    }
}
