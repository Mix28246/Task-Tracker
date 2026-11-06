package tracker.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import tracker.Fixtures

class BinarySerializerTest {
    private val serializer = BinaryTaskSerializer()

    private fun errorCode(file: Path): DataErrorCode = assertFailsWith<DataFileException> { serializer.load(file) }.code

    @Test
    fun roundTripReturnsSameRecords() {
        val original = JsonTaskSerializer().load(Fixtures.path("tasks.json"))
        val file = Files.createTempFile("roundtrip", ".bin")
        file.toFile().deleteOnExit()
        serializer.save(original, file)
        assertEquals(original.tasks.toList(), serializer.load(file).tasks.toList())
    }

    @Test
    fun referenceFileHasSameRecordsAsJson() {
        val expected = JsonTaskSerializer().load(Fixtures.path("tasks.json")).tasks.toList()
        assertEquals(expected, serializer.load(Fixtures.path("tasks.bin")).tasks.toList())
    }

    @Test
    fun truncatedFileIsReportedAsCorrupted() {
        assertEquals(DataErrorCode.CORRUPTED_FILE, errorCode(Fixtures.path("truncated.bin")))
    }

    @Test
    fun foreignObjectIsReportedAsCorrupted() {
        assertEquals(DataErrorCode.CORRUPTED_FILE, errorCode(Fixtures.path("not_archive.bin")))
    }

    @Test
    fun textFileIsReportedAsCorrupted() {
        assertEquals(DataErrorCode.CORRUPTED_FILE, errorCode(Fixtures.path("tasks.json")))
    }

    @Test
    fun newerVersionIsRejected() {
        assertEquals(DataErrorCode.UNSUPPORTED_VERSION, errorCode(Fixtures.path("wrong_version.bin")))
    }
}
