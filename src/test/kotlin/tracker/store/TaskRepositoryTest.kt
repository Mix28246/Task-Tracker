package tracker.store

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tracker.items
import tracker.model.RegularTask
import tracker.model.Task
import tracker.model.key

class TaskRepositoryTest {
    private val day = LocalDate.of(2026, 10, 1)

    private fun task(id: Int, days: Long, importance: Int = 5): Task =
        RegularTask(id, "Задача $id", "Иванов", day.plusDays(days), importance)

    @Test
    fun allIsSortedByDeadlineThenId() {
        val repository = TaskRepository()
        listOf(task(3, 2), task(1, 5), task(2, 2), task(4, 0)).forEach(repository::add)
        assertEquals(listOf(4, 2, 3, 1), repository.all().items().map { it.id })
    }

    @Test
    fun replaceWithNewDeadlineMovesTask() {
        val repository = TaskRepository()
        repository.add(task(1, 1))
        repository.add(task(2, 3))
        repository.replace(task(1, 10))
        assertEquals(listOf(2, 1), repository.all().items().map { it.id })
        assertEquals(day.plusDays(10), repository.findById(1)?.deadline)
        assertTrue(repository.inspect().isValid)
    }

    @Test
    fun duplicateIdIsRejected() {
        val repository = TaskRepository()
        repository.add(task(1, 1))
        assertFailsWith<TaskRepositoryException> { repository.add(task(1, 7)) }
        assertEquals(1, repository.size())
    }

    @Test
    fun removeDeletesFromBothIndexes() {
        val repository = TaskRepository()
        repository.add(task(1, 1))
        repository.add(task(2, 2))
        repository.remove(1)
        assertNull(repository.findById(1))
        assertEquals(listOf(2), repository.all().items().map { it.id })
        assertFailsWith<TaskRepositoryException> { repository.remove(1) }
    }

    @Test
    fun nextIdIsMaxPlusOne() {
        val repository = TaskRepository()
        assertEquals(1, repository.nextId())
        repository.add(task(7, 1))
        repository.add(task(3, 1))
        assertEquals(8, repository.nextId())
    }

    @Test
    fun rangeIncludesBothBorders() {
        val repository = TaskRepository()
        (1..10).forEach { repository.add(task(it, it.toLong())) }
        val found = repository.range(day.plusDays(3), day.plusDays(5))
        assertEquals(listOf(3, 4, 5), found.items().map { it.id })
    }

    @Test
    fun mostImportantComeFirst() {
        val repository = TaskRepository()
        (1..10).forEach { repository.add(task(it, it.toLong(), importance = it)) }
        assertEquals(listOf(10, 9, 8), repository.mostImportant(3).items().map { it.importance })
    }

    @Test
    fun lastPathEndsAtInsertedTask() {
        val repository = TaskRepository()
        (1..30).forEach { repository.add(task(it, it.toLong())) }
        val added = task(31, 15)
        repository.add(added)
        assertEquals(added.key, repository.lastPath().last())
    }

    @Test
    fun preparedTasksRejectDuplicateIds() {
        val prepared = TaskRepository().prepare()
        prepared.add(task(5, 1))
        assertFailsWith<TaskRepositoryException> { prepared.add(task(5, 2)) }
    }

    @Test
    fun installReplacesAllData() {
        val repository = TaskRepository()
        repository.add(task(1, 1))
        val prepared = repository.prepare()
        prepared.add(task(5, 1))
        repository.install(prepared)
        assertEquals(listOf(5), repository.all().items().map { it.id })
    }

    @Test
    fun copyIsNotAffectedByLaterChanges() {
        val repository = TaskRepository()
        repository.add(task(1, 1))
        val copy = repository.copy()
        repository.add(task(2, 2))
        assertEquals(1, copy.size)
    }

    @Test
    fun deadlineSearchFindsFirstTaskOfThatDay() {
        val repository = TaskRepository()
        listOf(task(3, 4), task(1, 4), task(2, 1)).forEach(repository::add)
        assertEquals(1, repository.locateDeadline(day.plusDays(4)).task?.id)
    }

    @Test
    fun deadlineSearchKeepsPathOnMiss() {
        val repository = TaskRepository()
        (1..20).forEach { repository.add(task(it, it.toLong() * 2)) }
        val found = repository.locateDeadline(day.plusDays(7))
        assertNull(found.task)
        assertTrue(found.path.size > 0)
    }
}
