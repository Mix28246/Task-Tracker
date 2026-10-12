package tracker.csv

import java.nio.file.Path

enum class CsvErrorCode(val description: String) {
    WRONG_FIELD_COUNT("неверное количество полей"),
    UNKNOWN_TYPE("неизвестный тип задачи"),
    BAD_NUMBER("поле должно быть целым числом"),
    BAD_DATE("неверная дата (нужен формат ГГГГ-ММ-ДД)"),
    OUT_OF_RANGE("значение вне допустимого диапазона"),
    INVALID_FIELD("некорректное значение поля"),
    DUPLICATE_ID("задача с таким ID уже встречалась в файле"),

    FILE_NOT_FOUND("файл не найден"),
    EMPTY_FILE("файл пустой"),
    BAD_HEADER("неверная строка заголовка"),
    IO_ERROR("ошибка чтения/записи файла"),
}

sealed class CsvException(
    val code: CsvErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class CsvLineException(
    code: CsvErrorCode,
    val lineNumber: Int,
    val details: String,
) : CsvException(code, "Строка $lineNumber: ${code.name} — ${code.description}: $details")

class CsvFileException(
    code: CsvErrorCode,
    val file: Path,
    cause: Throwable? = null,
) : CsvException(code, "${code.name} — ${code.description}: $file", cause)
