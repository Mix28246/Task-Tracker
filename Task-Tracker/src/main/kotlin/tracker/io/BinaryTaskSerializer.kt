package tracker.io

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamException
import java.nio.file.Files
import java.nio.file.Path

class BinaryTaskSerializer : Serializer<TaskArchive> {
    override val format: String = "Бинарный (Serializable)"

    override fun save(data: TaskArchive, file: Path) {
        try {
            ObjectOutputStream(BufferedOutputStream(Files.newOutputStream(file))).use { output -> output.writeObject(data) }
        } catch (e: IOException) {
            throw DataFileException(DataErrorCode.IO_ERROR, file, e.message ?: "", e)
        }
    }

    override fun load(file: Path): TaskArchive {
        val loaded = try {
            openForReading(file).use { raw -> ObjectInputStream(BufferedInputStream(raw)).readObject() }
        } catch (e: InvalidClassException) {
            throw DataFileException(DataErrorCode.UNSUPPORTED_VERSION, file, "класс в файле другой версии: ${e.message}", e)
        } catch (e: ObjectStreamException) {
            throw DataFileException(DataErrorCode.CORRUPTED_FILE, file, e.message ?: "", e)
        } catch (e: EOFException) {
            throw DataFileException(DataErrorCode.CORRUPTED_FILE, file, "файл обрывается раньше конца данных", e)
        } catch (e: ClassNotFoundException) {
            throw DataFileException(DataErrorCode.CORRUPTED_FILE, file, "неизвестный класс: ${e.message}", e)
        } catch (e: IOException) {
            throw DataFileException(DataErrorCode.IO_ERROR, file, e.message ?: "", e)
        }
        val archive = loaded as? TaskArchive
            ?: throw DataFileException(DataErrorCode.CORRUPTED_FILE, file, "в файле записан не архив задач")
        return checkVersion(archive, file)
    }
}
