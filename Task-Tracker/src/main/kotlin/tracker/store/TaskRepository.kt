package tracker.store

import java.time.LocalDate
import tracker.model.Task
import tracker.model.TaskKey
import tracker.model.key

class TaskRepositoryException(message: String) : Exception(message)

class SearchResult(val task: Task?, val path: ArrayBuffer<TaskKey>)

class PreparedTasks internal constructor(
    internal val byDeadline: TreapStore<TaskKey, Task>,
    internal val byId: TreapStore<Int, Task>,
) {
    val size: Int
        get() = byDeadline.size

    fun add(task: Task) {
        if (!tryAdd(task)) throw TaskRepositoryException("Задача с ID ${task.id} встречается дважды")
    }

    fun tryAdd(task: Task): Boolean {
        if (!byId.add(task)) return false
        byDeadline.add(task)
        return true
    }
}

class TaskRepository {
    private val lock = Any()
    private var byDeadline = newDeadlineTree()
    private var byId = newIdIndex()

    fun all(): ArrayBuffer<Task> = synchronized(lock) { byDeadline.all() }

    fun size(): Int = synchronized(lock) { byDeadline.size }

    fun findById(id: Int): Task? = synchronized(lock) { byId.findByKey(id) }

    fun locateDeadline(date: LocalDate): SearchResult = synchronized(lock) {
        val found = byDeadline.ceiling(TaskKey.firstOf(date))
        SearchResult(found?.takeIf { it.deadline == date }, byDeadline.lastPath)
    }

    fun taskAt(index: Int): Task? = synchronized(lock) { byDeadline.valueAt(index) }

    fun nextId(): Int = synchronized(lock) { (byId.maxKey() ?: 0) + 1 }

    fun add(task: Task): Unit = synchronized(lock) {
        if (byId.findByKey(task.id) != null) {
            throw TaskRepositoryException("Задача с ID ${task.id} уже существует")
        }
        byDeadline.add(task)
        byId.add(task)
    }

    fun replace(task: Task): Unit = synchronized(lock) {
        val old = byId.findByKey(task.id)
            ?: throw TaskRepositoryException("Задача с ID ${task.id} не найдена")
        byDeadline.removeByKey(old.key)
        byId.removeByKey(old.id)
        byDeadline.add(task)
        byId.add(task)
    }

    fun remove(id: Int): Task = synchronized(lock) {
        val old = byId.findByKey(id)
            ?: throw TaskRepositoryException("Задача с ID $id не найдена")
        byDeadline.removeByKey(old.key)
        byId.removeByKey(id)
        old
    }

    fun prepare(): PreparedTasks = PreparedTasks(newDeadlineTree(), newIdIndex())

    fun copy(): PreparedTasks = synchronized(lock) { PreparedTasks(byDeadline.copy(), byId.copy()) }

    fun install(prepared: PreparedTasks): Unit = synchronized(lock) {
        byDeadline = prepared.byDeadline
        byId = prepared.byId
    }

    fun range(from: LocalDate, to: LocalDate): ArrayBuffer<Task> = synchronized(lock) {
        byDeadline.range(TaskKey.firstOf(from), TaskKey.lastOf(to))
    }

    fun mostImportant(n: Int): ArrayBuffer<Task> = synchronized(lock) { byDeadline.top(n) }

    fun lastPath(): ArrayBuffer<TaskKey> = synchronized(lock) { byDeadline.lastPath }

    fun snapshot(maxDepth: Int, isFocused: (TaskKey) -> Boolean): TreeSnapshot<TaskKey, Task> = synchronized(lock) {
        byDeadline.snapshot(maxDepth, isFocused)
    }

    fun inspect(): StructureReport = synchronized(lock) { byDeadline.inspect() }

    private companion object {
        fun newDeadlineTree() = TreapStore<TaskKey, Task>(
            keyOf = { it.key },
            priorityOf = { it.importance },
            traceEnabled = true,
        )

        fun newIdIndex() = TreapStore<Int, Task>(
            keyOf = { it.id },
            priorityOf = { 0 },
        )
    }
}
