package tracker.io

import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

interface Serializer<T> {
    val format: String

    fun save(data: T, file: Path)

    fun load(file: Path): T
}

internal fun openForReading(file: Path): InputStream {
    try {
        if (Files.size(file) == 0L) throw DataFileException(DataErrorCode.EMPTY_FILE, file)
        return Files.newInputStream(file)
    } catch (e: NoSuchFileException) {
        throw DataFileException(DataErrorCode.FILE_NOT_FOUND, file, cause = e)
    } catch (e: IOException) {
        throw DataFileException(DataErrorCode.IO_ERROR, file, e.message ?: "", e)
    }
}

internal fun checkVersion(archive: TaskArchive, file: Path): TaskArchive {
    if (archive.version != TaskArchive.CURRENT_VERSION) {
        throw DataFileException(
            DataErrorCode.UNSUPPORTED_VERSION, file,
            "версия файла ${archive.version}, программа читает версию ${TaskArchive.CURRENT_VERSION}",
        )
    }
    return archive
}
