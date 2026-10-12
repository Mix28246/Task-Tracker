package tracker.service

import tracker.csv.CsvLoader
import tracker.csv.CsvWriter
import tracker.csv.LoadResult
import tracker.model.Editable
import tracker.model.RegularTask
import tracker.model.Task
import tracker.store.TaskRepository
import tracker.store.TaskRepositoryException
import java.nio.file.Path

class TaskService(
    private val repository: TaskRepository = TaskRepository(),
    private val loader: CsvLoader = CsvLoader(),
    private val writer: CsvWriter = CsvWriter(),
) {
    fun all(): List<Task> = repository.all()

    fun nextId(): Int = repository.nextId()

    fun load(file: Path): LoadResult {
        val result = loader.load(file)
        repository.replaceAll(result.tasks)
        return result
    }

    fun save(file: Path) = writer.save(repository.all(), file)

    fun add(task: RegularTask) {
        requireValid(task)
        repository.add(task)
    }

    fun update(task: RegularTask) {
        requireValid(task)
        val old = repository.findById(task.id)
            ?: throw TaskRepositoryException("Задача с ID ${task.id} не найдена")
        if (old !is Editable) {
            throw TaskRepositoryException("Задача #${task.id} доступна только для чтения")
        }
        if (old.type != task.type) {
            throw TaskRepositoryException("При редактировании нельзя менять тип задачи")
        }
        repository.replace(task)
    }

    private fun requireValid(task: Editable) {
        val errors = task.validate()
        if (errors.isNotEmpty()) throw TaskRepositoryException(errors.joinToString("\n"))
    }
}
