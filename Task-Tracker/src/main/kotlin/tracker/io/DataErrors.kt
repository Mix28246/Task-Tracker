package tracker.io

import java.nio.file.Path

enum class DataErrorCode(val description: String) {
    WRONG_FIELD_COUNT("неверное количество полей"),
    UNKNOWN_TYPE("неизвестный тип задачи"),
    BAD_NUMBER("поле должно быть целым числом"),
    BAD_DATE("неверная дата (нужен формат ГГГГ-ММ-ДД)"),
    OUT_OF_RANGE("значение вне допустимого диапазона"),
    INVALID_FIELD("некорректное значение поля"),
    DUPLICATE_ID("задача с таким ID уже встречалась в файле"),

    FILE_NOT_FOUND("файл не найден"),
    EMPTY_FILE("файл пустой"),
    UNKNOWN_FORMAT("неизвестный формат файла"),
    BAD_HEADER("неверная строка заголовка"),
    CORRUPTED_FILE("файл повреждён или не соответствует формату"),
    UNSUPPORTED_VERSION("неподдерживаемая версия формата"),
    IO_ERROR("ошибка чтения/записи файла"),
}

sealed class DataException(
    val code: DataErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class RecordException(
    code: DataErrorCode,
    val position: String,
    val details: String,
) : DataException(code, "$position: ${code.name} — ${code.description}: $details")

class DataFileException(
    code: DataErrorCode,
    val file: Path,
    val details: String = "",
    cause: Throwable? = null,
) : DataException(code, "${code.name} — ${code.description}: ${file.fileName}" + (if (details.isEmpty()) "" else "\n$details"), cause)
