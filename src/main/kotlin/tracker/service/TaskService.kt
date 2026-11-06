package tracker.service

import java.nio.file.Path
import java.time.LocalDate
import tracker.concurrent.CancellationToken
import tracker.concurrent.ProgressListener
import tracker.io.LoadResult
import tracker.io.TaskFiles
import tracker.model.Editable
import tracker.model.RegularTask
import tracker.model.Task
import tracker.model.TaskKey
import tracker.model.display
import tracker.store.ArrayBuffer
import tracker.store.PreparedTasks
import tracker.store.SearchResult
import tracker.store.TaskRepository
import tracker.store.TaskRepositoryException
import tracker.store.TreeSnapshot

class PreparedLoad(val result: LoadResult, val tasks: PreparedTasks)

class TaskService(
    private val repository: TaskRepository = TaskRepository(),
    private val files: TaskFiles = TaskFiles(),
    private val generator: TaskGenerator = TaskGenerator(),
) {
    fun all(): ArrayBuffer<Task> = repository.all()

    fun size(): Int = repository.size()

    fun nextId(): Int = repository.nextId()

    fun read(
        file: Path,
        token: CancellationToken = CancellationToken(),
        progress: ProgressListener = ProgressListener.NONE,
    ): PreparedLoad {
        val prepared = repository.prepare()
        val result = files.read(file, token, progress) { prepared.tryAdd(it) }
        return PreparedLoad(result, prepared)
    }

    fun load(file: Path): LoadResult {
        val prepared = read(file)
        install(prepared.tasks)
        return prepared.result
    }

    fun install(prepared: PreparedTasks) = repository.install(prepared)

    fun save(file: Path, progress: ProgressListener = ProgressListener.NONE) =
        files.write(file, repository.all(), progress)

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

    fun remove(id: Int): Task {
        val old = repository.findById(id)
            ?: throw TaskRepositoryException("Задача с ID $id не найдена")
        if (old !is Editable) {
            throw TaskRepositoryException("Задача #$id доступна только для чтения")
        }
        return repository.remove(id)
    }

    fun findById(id: Int): Task? = repository.findById(id)

    fun findByDeadline(date: LocalDate): SearchResult = repository.locateDeadline(date)

    fun taskAt(index: Int): Task? = repository.taskAt(index)

    fun range(from: LocalDate, to: LocalDate): ArrayBuffer<Task> {
        if (from > to) throw TaskRepositoryException("Начало интервала (${from.display()}) позже конца (${to.display()})")
        return repository.range(from, to)
    }

    fun mostImportant(n: Int): ArrayBuffer<Task> {
        if (n !in 1..MAX_TOP) throw TaskRepositoryException("N должно быть от 1 до $MAX_TOP")
        return repository.mostImportant(n)
    }

    fun prepareGenerated(
        count: Int,
        token: CancellationToken = CancellationToken(),
        progress: ProgressListener = ProgressListener.NONE,
    ): PreparedTasks {
        if (count !in 1..MAX_GENERATED) {
            throw TaskRepositoryException("Количество должно быть от 1 до $MAX_GENERATED")
        }
        val firstId = repository.nextId()
        val prepared = repository.copy()
        for (offset in 0 until count) {
            token.throwIfCancelled()
            prepared.add(generator.next(firstId + offset))
            progress.onProgress(GENERATE_STAGE, offset + 1, count)
        }
        return prepared
    }

    fun treeSnapshot(maxDepth: Int, isFocused: (TaskKey) -> Boolean = { false }): TreeSnapshot<TaskKey, Task> =
        repository.snapshot(maxDepth, isFocused)

    fun lastPath(): ArrayBuffer<TaskKey> = repository.lastPath()

    private fun requireValid(task: Editable) {
        val errors = task.validate()
        if (errors.isNotEmpty()) throw TaskRepositoryException(errors.joinToString("\n"))
    }

    companion object {
        const val MAX_GENERATED = 500_000
        const val MAX_TOP = 1_000
        const val GENERATE_STAGE = "Генерация задач"
    }
}
