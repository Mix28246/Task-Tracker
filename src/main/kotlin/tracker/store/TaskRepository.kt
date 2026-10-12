package tracker.store

import tracker.model.Task

class TaskRepositoryException(message: String) : Exception(message)

class TaskRepository {
    private val tasks = mutableListOf<Task>()

    fun all(): List<Task> = tasks.toList()

    fun size(): Int = tasks.size

    fun findById(id: Int): Task? = tasks.firstOrNull { it.id == id }

    fun nextId(): Int = (tasks.maxOfOrNull { it.id } ?: 0) + 1

    fun add(task: Task) {
        if (findById(task.id) != null) {
            throw TaskRepositoryException("Задача с ID ${task.id} уже существует")
        }
        tasks.add(task)
    }

    fun replace(task: Task) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index == -1) {
            throw TaskRepositoryException("Задача с ID ${task.id} не найдена")
        }
        tasks[index] = task
    }

    fun replaceAll(newTasks: List<Task>) {
        tasks.clear()
        tasks.addAll(newTasks)
    }
}
