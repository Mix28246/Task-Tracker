package tracker.io

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class JsonTaskSerializer : Serializer<TaskArchive> {
    override val format: String = "JSON"

    private val mapper: ObjectMapper = ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)

    override fun save(data: TaskArchive, file: Path) {
        try {
            Files.newOutputStream(file).use { output -> mapper.writeValue(output, data) }
        } catch (e: IOException) {
            throw DataFileException(DataErrorCode.IO_ERROR, file, e.message ?: "", e)
        }
    }

    override fun load(file: Path): TaskArchive {
        val archive = try {
            openForReading(file).use { input -> mapper.readValue(input, TaskArchive::class.java) }
        } catch (e: JsonProcessingException) {
            throw DataFileException(DataErrorCode.CORRUPTED_FILE, file, e.originalMessage, e)
        } catch (e: IOException) {
            throw DataFileException(DataErrorCode.IO_ERROR, file, e.message ?: "", e)
        }
        return checkVersion(archive, file)
    }
}
