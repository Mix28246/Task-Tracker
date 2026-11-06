package tracker.ui

import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import tracker.model.TaskType

class TaskFormTest {
    private val form = TaskForm(id = 1, type = TaskType.REGULAR, title = "Отчёт", assignee = "Иванов", deadline = "05.11.26")

    @Test
    fun validFormGivesTaskWithParsedDeadline() {
        val result = form.toTask()
        assertEquals(LocalDate.of(2026, 11, 5), (result as FormResult.Valid).task.deadline)
    }

    @Test
    fun isoDateIsRejected() {
        assertTrue(form.copy(deadline = "2026-11-05").toTask() is FormResult.Invalid)
    }

    @Test
    fun archivedTypeIsRejected() {
        assertTrue(form.copy(type = TaskType.ARCHIVED).toTask() is FormResult.Invalid)
    }

    @Test
    fun urgentWithoutDutyPersonIsRejected() {
        assertTrue(form.copy(type = TaskType.URGENT).toTask() is FormResult.Invalid)
    }
}
