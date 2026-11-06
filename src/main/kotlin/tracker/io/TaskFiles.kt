package tracker.io

import java.nio.file.Path
import tracker.concurrent.CancellationToken
import tracker.concurrent.ProgressListener
import tracker.model.Task
import tracker.store.ArrayBuffer

enum class FileFormat(val title: String, val extension: String) {
    CSV("CSV", "csv"),
    JSON("JSON", "json"),
    BINARY("Бинарный", "bin");

    companion object {
        fun of(file: Path): FileFormat? {
            val name = file.fileName.toString()
            return entries.firstOrNull { name.endsWith(".${it.extension}", ignoreCase = true) }
        }
    }
}

class TaskFiles(
    private val csvLoader: CsvLoader = CsvLoader(),
    private val csvWriter: CsvWriter = CsvWriter(),
    private val json: Serializer<TaskArchive> = JsonTaskSerializer(),
    private val binary: Serializer<TaskArchive> = BinaryTaskSerializer(),
) {
    fun read(
        file: Path,
        token: CancellationToken,
        progress: ProgressListener,
        accept: (Task) -> Boolean,
    ): LoadResult = when (formatOf(file)) {
        FileFormat.CSV -> csvLoader.load(file, token, progress, accept)
        FileFormat.JSON -> readArchive(json, file, token, progress, accept)
        FileFormat.BINARY -> readArchive(binary, file, token, progress, accept)
    }

    fun write(file: Path, tasks: ArrayBuffer<Task>, progress: ProgressListener) {
        when (formatOf(file)) {
            FileFormat.CSV -> csvWriter.save(file, tasks, progress)
            FileFormat.JSON -> writeArchive(json, file, tasks, progress)
            FileFormat.BINARY -> writeArchive(binary, file, tasks, progress)
        }
    }

    private fun readArchive(
        serializer: Serializer<TaskArchive>,
        file: Path,
        token: CancellationToken,
        progress: ProgressListener,
        accept: (Task) -> Boolean,
    ): LoadResult {
        progress.onProgress(CsvLoader.READ_STAGE, 0, 1)
        val records = serializer.load(file).tasks
        val skipped = ArrayBuffer<RecordException>()
        var loaded = 0
        for (index in records.indices) {
            token.throwIfCancelled()
            val position = "запись ${index + 1}"
            try {
                val task = records[index].toTask(position)
                if (!accept(task)) throw RecordException(DataErrorCode.DUPLICATE_ID, position, "id = ${task.id}")
                loaded++
            } catch (e: RecordException) {
                skipped.add(e)
            }
            progress.onProgress(BUILD_STAGE, index + 1, records.size)
        }
        return LoadResult(loaded, skipped)
    }

    private fun writeArchive(serializer: Serializer<TaskArchive>, file: Path, tasks: ArrayBuffer<Task>, progress: ProgressListener) {
        progress.onProgress(CsvWriter.WRITE_STAGE, 0, 1)
        serializer.save(TaskArchive.of(tasks), file)
        progress.onProgress(CsvWriter.WRITE_STAGE, 1, 1)
    }

    private fun formatOf(file: Path): FileFormat =
        FileFormat.of(file) ?: throw DataFileException(
            DataErrorCode.UNKNOWN_FORMAT, file,
            "поддерживаются " + FileFormat.entries.joinToString { ".${it.extension}" },
        )

    companion object {
        const val BUILD_STAGE = "Построение дерева"
    }
}
