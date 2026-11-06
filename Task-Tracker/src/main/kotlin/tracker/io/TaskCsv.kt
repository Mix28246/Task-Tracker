package tracker.io

import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import tracker.concurrent.CancellationToken
import tracker.concurrent.ProgressListener
import tracker.model.Task
import tracker.store.ArrayBuffer

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
        val position = "строка $lineNumber"
        val parts = line.split(SEPARATOR).map { it.trim() }
        if (parts.size != FIELD_COUNT) {
            throw RecordException(DataErrorCode.WRONG_FIELD_COUNT, position, "ожидалось $FIELD_COUNT, получено ${parts.size}")
        }
        val record = TaskRecord(
            type = parts[COL_TYPE],
            id = parseInt(parts[COL_ID], "id", position),
            title = parts[COL_TITLE],
            assignee = parts[COL_ASSIGNEE],
            deadline = parts[COL_DEADLINE],
            importance = parseInt(parts[COL_IMPORTANCE], "importance", position),
            dutyPerson = parts[COL_DUTY_PERSON],
        )
        return record.toTask(position)
    }

    fun toLine(task: Task): String {
        val record = TaskRecord.of(task)
        return arrayOf(
            record.type, record.id, record.title, record.assignee, record.deadline, record.importance, record.dutyPerson,
        ).joinToString(SEPARATOR)
    }

    private fun parseInt(value: String, field: String, position: String): Int =
        value.toIntOrNull() ?: throw RecordException(DataErrorCode.BAD_NUMBER, position, "$field = «$value»")
}

class LoadResult(
    val loaded: Int,
    val skipped: ArrayBuffer<RecordException>,
)

class CsvLoader {
    fun load(
        file: Path,
        token: CancellationToken = CancellationToken(),
        progress: ProgressListener = ProgressListener.NONE,
        accept: (Task) -> Boolean,
    ): LoadResult =
        try {
            val totalKb = kilobytes(Files.size(file)).coerceAtLeast(1)
            CountingInputStream(Files.newInputStream(file)).use { input ->
                val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
                val header = reader.readLine() ?: throw DataFileException(DataErrorCode.EMPTY_FILE, file)
                if (header.removePrefix(UTF8_BOM).trim() != TaskCsvFormat.HEADER) {
                    throw DataFileException(DataErrorCode.BAD_HEADER, file, "ожидалось: ${TaskCsvFormat.HEADER}")
                }
                val skipped = ArrayBuffer<RecordException>()
                var loaded = 0
                var lineNumber = 1
                while (true) {
                    val line = reader.readLine() ?: break
                    lineNumber++
                    token.throwIfCancelled()
                    progress.onProgress(READ_STAGE, kilobytes(input.count).coerceAtMost(totalKb), totalKb)
                    if (line.isBlank()) continue
                    try {
                        val task = TaskCsvFormat.parseLine(line, lineNumber)
                        if (!accept(task)) {
                            throw RecordException(DataErrorCode.DUPLICATE_ID, "строка $lineNumber", "id = ${task.id}")
                        }
                        loaded++
                    } catch (e: RecordException) {
                        skipped.add(e)
                    }
                }
                progress.onProgress(READ_STAGE, totalKb, totalKb)
                LoadResult(loaded, skipped)
            }
        } catch (e: NoSuchFileException) {
            throw DataFileException(DataErrorCode.FILE_NOT_FOUND, file, cause = e)
        } catch (e: IOException) {
            throw DataFileException(DataErrorCode.IO_ERROR, file, e.message ?: "", e)
        }

    private class CountingInputStream(source: InputStream) : FilterInputStream(source) {
        var count = 0L
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) count++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) count += read
            return read
        }
    }

    companion object {
        const val READ_STAGE = "Чтение файла"
        private const val UTF8_BOM = "﻿"
        private const val BYTES_IN_KB = 1024L

        private fun kilobytes(bytes: Long): Int = (bytes / BYTES_IN_KB).toInt()
    }
}

class CsvWriter {
    fun save(file: Path, tasks: ArrayBuffer<Task>, progress: ProgressListener = ProgressListener.NONE) {
        try {
            Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { writer ->
                writer.write(TaskCsvFormat.HEADER)
                writer.newLine()
                for (index in 0 until tasks.size) {
                    writer.write(TaskCsvFormat.toLine(tasks[index]))
                    writer.newLine()
                    progress.onProgress(WRITE_STAGE, index + 1, tasks.size)
                }
            }
        } catch (e: IOException) {
            throw DataFileException(DataErrorCode.IO_ERROR, file, e.message ?: "", e)
        }
    }

    companion object {
        const val WRITE_STAGE = "Запись файла"
    }
}
