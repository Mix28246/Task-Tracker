package tracker.io

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable
import java.time.LocalDate
import java.time.format.DateTimeParseException
import tracker.model.ArchivedTask
import tracker.model.RegularTask
import tracker.model.Task
import tracker.model.TaskRules
import tracker.model.TaskType
import tracker.model.UrgentTask

data class TaskRecord @JsonCreator constructor(
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("id") val id: Int,
    @param:JsonProperty("title") val title: String,
    @param:JsonProperty("assignee") val assignee: String,
    @param:JsonProperty("deadline") val deadline: String,
    @param:JsonProperty("importance") val importance: Int,
    @param:JsonProperty("dutyPerson") val dutyPerson: String,
) : Serializable {
    fun toTask(position: String): Task {
        val taskType = TaskType.fromCode(type)
            ?: throw RecordException(DataErrorCode.UNKNOWN_TYPE, position, "«$type»")
        val date = try {
            LocalDate.parse(deadline)
        } catch (e: DateTimeParseException) {
            throw RecordException(DataErrorCode.BAD_DATE, position, "«$deadline»")
        }
        if (id <= 0) throw RecordException(DataErrorCode.OUT_OF_RANGE, position, "id = $id, должен быть > 0")
        if (!TaskRules.isImportanceValid(importance)) {
            throw RecordException(
                DataErrorCode.OUT_OF_RANGE, position,
                "importance = $importance, допустимо ${TaskRules.MIN_IMPORTANCE}..${TaskRules.MAX_IMPORTANCE}",
            )
        }
        val textErrors = TaskRules.validateText(TaskRules.TITLE_FIELD, title) +
            TaskRules.validateText(TaskRules.ASSIGNEE_FIELD, assignee)
        if (textErrors.isNotEmpty()) {
            throw RecordException(DataErrorCode.INVALID_FIELD, position, textErrors.joinToString("; "))
        }
        return when (taskType) {
            TaskType.REGULAR -> RegularTask(id, title, assignee, date, importance)
            TaskType.ARCHIVED -> ArchivedTask(id, title, assignee, date, importance)
            TaskType.URGENT -> {
                val dutyErrors = TaskRules.validateText(TaskRules.DUTY_FIELD, dutyPerson)
                if (dutyErrors.isNotEmpty()) {
                    throw RecordException(DataErrorCode.INVALID_FIELD, position, "у срочной задачи: ${dutyErrors.first()}")
                }
                UrgentTask(id, title, assignee, date, importance, dutyPerson)
            }
        }
    }

    companion object {
        private const val serialVersionUID = 1L

        fun of(task: Task): TaskRecord = TaskRecord(
            type = task.type.code,
            id = task.id,
            title = task.title,
            assignee = task.assignee,
            deadline = task.deadline.toString(),
            importance = task.importance,
            dutyPerson = when (task) {
                is UrgentTask -> task.dutyPerson
                is RegularTask, is ArchivedTask -> ""
            },
        )
    }
}
