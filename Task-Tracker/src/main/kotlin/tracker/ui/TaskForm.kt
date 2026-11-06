package tracker.ui

import java.time.LocalDate
import tracker.model.DisplayDate
import tracker.model.RegularTask
import tracker.model.Task
import tracker.model.TaskType
import tracker.model.UrgentTask
import tracker.model.display

sealed interface FormResult {
    data class Valid(val task: RegularTask) : FormResult
    data class Invalid(val errors: List<String>) : FormResult
}

data class TaskForm(
    val id: Int,
    val type: TaskType,
    val title: String = "",
    val assignee: String = "",
    val deadline: String = LocalDate.now().display(),
    val importance: String = DEFAULT_IMPORTANCE.toString(),
    val dutyPerson: String = "",
) {
    fun toTask(): FormResult {
        val errors = mutableListOf<String>()

        val importanceValue = importance.trim().toIntOrNull()
        if (importanceValue == null) errors.add("Важность должна быть целым числом")

        val deadlineValue = DisplayDate.parse(deadline)
        if (deadlineValue == null) errors.add("Срок: неверная дата, нужен формат ${DisplayDate.PATTERN_HINT}")

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
            deadline = task.deadline.display(),
            importance = task.importance.toString(),
            dutyPerson = (task as? UrgentTask)?.dutyPerson ?: "",
        )
    }
}
