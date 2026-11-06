package tracker.service

import java.nio.file.Files
import java.time.LocalDate
import kotlin.random.Random
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import tracker.items
import tracker.concurrent.CancellationToken
import tracker.concurrent.OperationCancelledException
import tracker.concurrent.ProgressListener
import tracker.Fixtures
import tracker.io.TaskCsvFormat
import tracker.model.RegularTask
import tracker.model.UrgentTask
import tracker.store.TaskRepositoryException

class TaskServiceTest {
    private fun serviceWithArchived(): TaskService {
        val path = Files.createTempFile("tasks", ".csv")
        path.toFile().deleteOnExit()
        Files.write(
            path,
            listOf(
                TaskCsvFormat.HEADER,
                "REGULAR;1;Обычная;Иванов;2026-10-10;5;",
                "ARCHIVED;2;Архивная;Петрова;2026-09-01;3;",
            ),
        )
        val service = TaskService(generator = TaskGenerator(Random(1)))
        service.load(path)
        return service
    }

    @Test
    fun archivedTaskCannotBeRemoved() {
        val service = serviceWithArchived()
        assertFailsWith<TaskRepositoryException> { service.remove(2) }
        assertEquals(2, service.size())
    }

    @Test
    fun archivedTaskCannotBeChanged() {
        val service = serviceWithArchived()
        val forged = RegularTask(2, "Подмена", "Иванов", LocalDate.of(2026, 10, 1), 5)
        assertFailsWith<TaskRepositoryException> { service.update(forged) }
    }

    @Test
    fun editableTaskCanBeRemoved() {
        val service = serviceWithArchived()
        service.remove(1)
        assertEquals(listOf(2), service.all().items().map { it.id })
    }

    @Test
    fun invalidTaskIsRejected() {
        val service = serviceWithArchived()
        val bad = UrgentTask(3, "", "Иванов", LocalDate.of(2026, 10, 1), 11, "")
        val error = assertFailsWith<TaskRepositoryException> { service.add(bad) }
        assertTrue(error.message!!.contains("Дежурный"))
    }

    @Test
    fun generatedTasksAreValidAndUnique() {
        val service = serviceWithArchived()
        service.install(service.prepareGenerated(2_000))
        val generated = service.all().items().filterIsInstance<RegularTask>().filter { it.id > 2 }
        assertTrue(generated.all { it.validate().isEmpty() })
        assertEquals(2_000, generated.map { it.id }.toSet().size)
    }

    @Test
    fun cancelledGenerationKeepsOldData() {
        val service = serviceWithArchived()
        val token = CancellationToken()
        assertFailsWith<OperationCancelledException> {
            service.prepareGenerated(10_000, token, ProgressListener { _, done, _ -> if (done == 100) token.cancel() })
        }
        assertEquals(listOf(2, 1), service.all().items().map { it.id })
    }

    @Test
    fun preparedTasksAreInvisibleUntilInstalled() {
        val service = serviceWithArchived()
        val prepared = service.prepareGenerated(50)
        assertEquals(2, service.size())
        service.install(prepared)
        assertEquals(52, service.size())
        assertEquals(53, service.nextId())
    }

    @Test
    fun reversedRangeIsRejected() {
        val service = serviceWithArchived()
        assertFailsWith<TaskRepositoryException> { service.range(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 1, 1)) }
    }

    @Test
    fun nonPositiveTopIsRejected() {
        val service = serviceWithArchived()
        assertFailsWith<TaskRepositoryException> { service.mostImportant(0) }
    }

    @Test
    fun equalImportanceIsOrderedByDeadline() {
        val service = TaskService()
        val day = LocalDate.of(2026, 10, 1)
        listOf(5L, 1L, 3L).forEachIndexed { index, days ->
            service.add(RegularTask(index + 1, "Задача", "Иванов", day.plusDays(days), 7))
        }
        service.add(RegularTask(4, "Важнее", "Иванов", day.plusDays(9), 9))
        assertEquals(listOf(4, 2, 3, 1), service.mostImportant(4).items().map { it.id })
    }

    @Test
    fun importedJsonIsOrderedByDeadline() {
        val service = TaskService()
        service.load(Fixtures.path("tasks.json"))
        val deadlines = service.all().items().map { it.deadline }
        assertEquals(deadlines.sorted(), deadlines)
    }
}
