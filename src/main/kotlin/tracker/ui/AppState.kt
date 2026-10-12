package tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException
import tracker.csv.CsvFileException
import tracker.csv.CsvLineException
import tracker.model.Editable
import tracker.model.RegularTask
import tracker.model.Task
import tracker.model.TaskType
import tracker.model.UrgentTask
import tracker.service.TaskService
import tracker.store.TaskRepositoryException

sealed interface DialogRequest {
    data class Editor(val form: TaskForm, val isNew: Boolean) : DialogRequest

    data class Error(val title: String, val text: String) : DialogRequest

    data class LoadReport(val loadedCount: Int, val skipped: List<CsvLineException>) : DialogRequest
}

class AppState(private val service: TaskService) {
    var tasks: List<Task> by mutableStateOf(service.all())
        private set

    var selectedId: Int? by mutableStateOf(null)
        private set

    var dialog: DialogRequest? by mutableStateOf(null)
        private set

    var currentFile: Path? by mutableStateOf(null)
        private set

    var statusMessage: String by mutableStateOf("Загрузите CSV-файл или добавьте задачу")
        private set

    val selectedTask: Task?
        get() = tasks.firstOrNull { it.id == selectedId }

    val canEditSelected: Boolean
        get() = selectedTask is Editable

    fun select(id: Int) {
        selectedId = id
    }

    fun load(file: Path) {
        try {
            val result = service.load(file)
            refresh()
            selectedId = null
            currentFile = file
            statusMessage = "Загружено задач: ${result.tasks.size}, пропущено строк: ${result.skipped.size}"
            if (result.skipped.isNotEmpty()) {
                dialog = DialogRequest.LoadReport(result.tasks.size, result.skipped)
            }
        } catch (e: CsvFileException) {
            showError("Не удалось загрузить файл", e)
        }
    }

    fun save(file: Path) {
        try {
            service.save(file)
            currentFile = file
            statusMessage = "Сохранено задач: ${tasks.size} → ${file.fileName}"
        } catch (e: CsvFileException) {
            showError("Не удалось сохранить файл", e)
        }
    }

    fun startAdd() {
        dialog = DialogRequest.Editor(TaskForm(id = service.nextId(), type = TaskForm.CREATABLE_TYPES.first()), isNew = true)
    }

    fun startEdit() {
        val task = selectedTask

        if (task !is Editable) {
            dialog = DialogRequest.Error("Редактирование невозможно", "Эта задача доступна только для чтения")
            return
        }
        dialog = DialogRequest.Editor(TaskForm.from(task), isNew = false)
    }

    fun submit(form: TaskForm, isNew: Boolean): List<String> {
        val task = when (val result = form.toTask()) {
            is FormResult.Invalid -> return result.errors
            is FormResult.Valid -> result.task
        }
        try {
            if (isNew) service.add(task) else service.update(task)
        } catch (e: TaskRepositoryException) {
            return listOf(e.message ?: "Неизвестная ошибка")
        }
        refresh()
        selectedId = task.id
        statusMessage = if (isNew) "Добавлена задача #${task.id}" else "Изменена задача #${task.id}"
        dialog = null
        return emptyList()
    }

    fun closeDialog() {
        dialog = null
    }

    private fun refresh() {
        tasks = service.all()
    }

    private fun showError(title: String, e: CsvFileException) {
        val cause = e.cause?.message?.let { "\n\nПодробности: $it" } ?: ""
        dialog = DialogRequest.Error(title, (e.message ?: e.code.description) + cause)
    }
}

sealed interface FormResult {
    data class Valid(val task: RegularTask) : FormResult
    data class Invalid(val errors: List<String>) : FormResult
}

data class TaskForm(
    val id: Int,
    val type: TaskType,
    val title: String = "",
    val assignee: String = "",
    val deadline: String = LocalDate.now().toString(),
    val importance: String = DEFAULT_IMPORTANCE.toString(),
    val dutyPerson: String = "",
) {
    fun toTask(): FormResult {
        val errors = mutableListOf<String>()

        val importanceValue = importance.trim().toIntOrNull()
        if (importanceValue == null) errors.add("Важность должна быть целым числом")

        val deadlineValue = try {
            LocalDate.parse(deadline.trim())
        } catch (e: DateTimeParseException) {
            errors.add("Срок: неверная дата, нужен формат ГГГГ-ММ-ДД")
            null
        }

        if (importanceValue == null || deadlineValue == null) return FormResult.Invalid(errors)

        val task: RegularTask = when (type) {
            TaskType.REGULAR -> RegularTask(id, title.trim(), assignee.trim(), deadlineValue, importanceValue)
            TaskType.URGENT -> UrgentTask(
                id, title.trim(), assignee.trim(), deadlineValue, importanceValue, dutyPerson.trim(),
            )
            TaskType.ARCHIVED -> return FormResult.Invalid(listOf("Архивную задачу нельзя создать или изменить вручную"))
        }

        val validationErrors = task.validate()
        return if (validationErrors.isEmpty()) FormResult.Valid(task) else FormResult.Invalid(validationErrors)
    }

    companion object {
        const val DEFAULT_IMPORTANCE = 5

        val CREATABLE_TYPES = listOf(TaskType.REGULAR, TaskType.URGENT)

        fun from(task: Task): TaskForm = TaskForm(
            id = task.id,
            type = task.type,
            title = task.title,
            assignee = task.assignee,
            deadline = task.deadline.toString(),
            importance = task.importance.toString(),
            dutyPerson = (task as? UrgentTask)?.dutyPerson ?: "",
        )
    }
}
