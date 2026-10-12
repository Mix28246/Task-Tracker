package tracker.model

import java.time.LocalDate

sealed class Task(
    val id: Int,
    val title: String,
    val assignee: String,
    val deadline: LocalDate,
    val importance: Int,
) {
    abstract val type: TaskType

    fun isOverdue(today: LocalDate = LocalDate.now()): Boolean = deadline.isBefore(today)

    override fun toString(): String =
        "${type.displayName} #$id «$title» (исполнитель: $assignee, срок: $deadline, важность: $importance)"
}

open class RegularTask(
    id: Int,
    title: String,
    assignee: String,
    deadline: LocalDate,
    importance: Int,
) : Task(id, title, assignee, deadline, importance), Editable {
    override val type: TaskType
        get() = TaskType.REGULAR

    override fun validate(): List<String> = TaskRules.validateCommon(id, title, assignee, importance)
}

class UrgentTask(
    id: Int,
    title: String,
    assignee: String,
    deadline: LocalDate,
    importance: Int,
    val dutyPerson: String,
) : RegularTask(id, title, assignee, deadline, importance) {
    override val type: TaskType
        get() = TaskType.URGENT

    override fun validate(): List<String> =
        super.validate() + TaskRules.validateText("Дежурный", dutyPerson)

    override fun toString(): String = super.toString() + ", дежурный: $dutyPerson"
}

class ArchivedTask(
    id: Int,
    title: String,
    assignee: String,
    deadline: LocalDate,
    importance: Int,
) : Task(id, title, assignee, deadline, importance) {
    override val type: TaskType
        get() = TaskType.ARCHIVED
}

interface Editable {
    fun validate(): List<String>
}

enum class TaskType(val csvCode: String, val displayName: String) {
    REGULAR("REGULAR", "Обычная"),
    URGENT("URGENT", "Срочная"),
    ARCHIVED("ARCHIVED", "Архивная");

    companion object {
        fun fromCsvCode(code: String): TaskType? = entries.firstOrNull { it.csvCode == code }
    }
}

object TaskRules {
    const val MIN_IMPORTANCE = 1
    const val MAX_IMPORTANCE = 10

    private val FORBIDDEN_CHARS = charArrayOf(';', '\n', '\r')

    fun isImportanceValid(importance: Int): Boolean = importance in MIN_IMPORTANCE..MAX_IMPORTANCE

    fun validateCommon(id: Int, title: String, assignee: String, importance: Int): List<String> {
        val errors = mutableListOf<String>()
        if (id <= 0) errors.add("ID должен быть положительным числом")
        errors.addAll(validateText("Название", title))
        errors.addAll(validateText("Исполнитель", assignee))
        if (!isImportanceValid(importance)) {
            errors.add("Важность должна быть от $MIN_IMPORTANCE до $MAX_IMPORTANCE")
        }
        return errors
    }

    fun validateText(fieldName: String, value: String): List<String> = when {
        value.isBlank() -> listOf("Поле «$fieldName» не может быть пустым")
        value.any { it in FORBIDDEN_CHARS } -> listOf("Поле «$fieldName» не должно содержать ';' и переносы строк")
        else -> emptyList()
    }
}
