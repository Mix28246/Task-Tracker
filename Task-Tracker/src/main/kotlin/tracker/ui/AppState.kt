package tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Path
import tracker.concurrent.OperationCancelledException
import tracker.io.DataFileException
import tracker.io.FileFormat
import tracker.io.RecordException
import tracker.model.DisplayDate
import tracker.model.Editable
import tracker.model.Task
import tracker.model.display
import tracker.model.key
import tracker.service.TaskService
import tracker.store.ArrayBuffer
import tracker.store.SearchResult
import tracker.store.TaskRepositoryException

sealed interface DialogRequest {
    data class Editor(val form: TaskForm, val isNew: Boolean) : DialogRequest

    data class Error(val title: String, val text: String) : DialogRequest

    class LoadReport(val loadedCount: Int, val skipped: ArrayBuffer<RecordException>) : DialogRequest

    data class ConfirmDelete(val task: Task) : DialogRequest
}

enum class Screen(val title: String) {
    TABLE("Таблица"),
    TREE("Дерево"),
}

class AppState(private val service: TaskService, val runner: BackgroundRunner) {
    val table = TableViewState(service)
    val tree = TreeViewState(service)

    var selectedTask: Task? by mutableStateOf(null)
        private set

    var dialog: DialogRequest? by mutableStateOf(null)
        private set

    var currentFile: Path? by mutableStateOf(null)
        private set

    var statusMessage: String by mutableStateOf("Импортируйте файл, сгенерируйте или добавьте задачи")
        private set

    var screen: Screen by mutableStateOf(Screen.TABLE)
        private set

    val selectedId: Int?
        get() = selectedTask?.id

    val canEditSelected: Boolean
        get() = !runner.isBusy && selectedTask is Editable

    init {
        refresh()
    }

    fun select(id: Int) {
        selectedTask = service.findById(id)
    }

    fun open(target: Screen) {
        screen = target
    }

    fun importFile(file: Path) {
        runner.start("Импорт", work = { token, progress -> service.read(file, token, progress) }) { outcome ->
            outcome.onSuccess { prepared ->
                service.install(prepared.tasks)
                val result = prepared.result
                selectedTask = null
                currentFile = file
                table.applyFilter(null)
                tree.clearHighlight()
                statusMessage = "Импорт ${formatName(file)}: загружено задач ${result.loaded}, пропущено записей ${result.skipped.size}"
                if (!result.skipped.isEmpty()) {
                    dialog = DialogRequest.LoadReport(result.loaded, result.skipped)
                }
            }.onFailure { handleFailure("Не удалось импортировать файл", it) }
        }
    }

    fun exportFile(file: Path) {
        runner.start("Экспорт", cancellable = false, work = { _, progress -> service.save(file, progress) }) { outcome ->
            outcome.onSuccess {
                currentFile = file
                statusMessage = "Экспорт ${formatName(file)}: сохранено задач ${table.totalCount} → ${file.fileName}"
            }.onFailure { handleFailure("Не удалось экспортировать файл", it) }
        }
    }

    fun generate(countText: String) {
        val count = parseNumber(countText, 1..TaskService.MAX_GENERATED)
            ?: return showError("Неверное количество", "Введите целое число от 1 до ${TaskService.MAX_GENERATED}")
        runner.start(
            "Генерация",
            work = { token, progress -> service.prepareGenerated(count, token, progress) },
        ) { outcome ->
            outcome.onSuccess { prepared ->
                service.install(prepared)
                tree.clearHighlight()
                refresh()
                statusMessage = "Сгенерировано задач: $count, всего: ${prepared.size}"
            }.onFailure { handleFailure("Не удалось сгенерировать задачи", it) }
        }
    }

    fun cancelJob() {
        runner.cancel()
    }

    fun startAdd() {
        if (runner.isBusy) return
        dialog = DialogRequest.Editor(TaskForm(id = service.nextId(), type = TaskForm.CREATABLE_TYPES.first()), isNew = true)
    }

    fun startEdit() {
        if (runner.isBusy) return
        val task = selectedTask
        if (task !is Editable) return showError("Редактирование невозможно", READ_ONLY)
        dialog = DialogRequest.Editor(TaskForm.from(task), isNew = false)
    }

    fun submit(form: TaskForm, isNew: Boolean): List<String> {
        if (runner.isBusy) return listOf(WAIT_FOR_JOB)
        val task = when (val result = form.toTask()) {
            is FormResult.Invalid -> return result.errors
            is FormResult.Valid -> result.task
        }
        try {
            if (isNew) service.add(task) else service.update(task)
        } catch (e: TaskRepositoryException) {
            return listOf(e.message ?: UNKNOWN_ERROR)
        }
        selectedTask = task
        tree.highlight(service.lastPath(), task.key)
        refresh()
        statusMessage = if (isNew) "Добавлена задача #${task.id}" else "Изменена задача #${task.id}"
        dialog = null
        return emptyList()
    }

    fun requestDelete() {
        if (runner.isBusy) return
        val task = selectedTask
        if (task !is Editable) return showError("Удаление невозможно", READ_ONLY)
        dialog = DialogRequest.ConfirmDelete(task)
    }

    fun confirmDelete(task: Task) {
        dialog = null
        if (runner.isBusy) return showError("Удаление невозможно", WAIT_FOR_JOB)
        try {
            service.remove(task.id)
        } catch (e: TaskRepositoryException) {
            return showError("Удаление невозможно", e.message ?: UNKNOWN_ERROR)
        }
        tree.highlight(service.lastPath(), null)
        refresh()
        statusMessage = "Удалена задача #${task.id}"
    }

    fun applyRange(fromText: String, toText: String) {
        val from = DisplayDate.parse(fromText) ?: return showError("Неверная дата", "Начало интервала: $DATE_FORMAT")
        val to = DisplayDate.parse(toText) ?: return showError("Неверная дата", "Конец интервала: $DATE_FORMAT")
        if (from > to) return showError("Неверный интервал", "Начало интервала (${from.display()}) позже конца (${to.display()})")
        table.applyFilter(TaskFilter.Range(from, to))
        statusMessage = "Задачи со сроком с ${from.display()} по ${to.display()}: ${table.rowCount}"
    }

    fun applyTop(nText: String) {
        val n = parseNumber(nText, 1..TaskService.MAX_TOP)
            ?: return showError("Неверное N", "N должно быть целым числом от 1 до ${TaskService.MAX_TOP}")
        table.applyFilter(TaskFilter.Top(n))
        statusMessage = "$n самых важных задач: показано ${table.rowCount}"
    }

    fun clearFilter() {
        table.applyFilter(null)
        statusMessage = "Фильтр сброшен"
    }

    fun findByDeadline(dateText: String) {
        if (runner.isBusy) return
        val date = DisplayDate.parse(dateText) ?: return showError("Неверная дата", "Срок: $DATE_FORMAT")
        showSearch(service.findByDeadline(date), "Задача со сроком ${date.display()}")
    }

    fun closeDialog() {
        dialog = null
    }

    fun showError(title: String, text: String) {
        dialog = DialogRequest.Error(title, text)
    }

    private fun showSearch(found: SearchResult, what: String) {
        val task = found.task
        if (task != null) selectedTask = task
        tree.highlight(found.path, task?.key)
        statusMessage = if (task == null) {
            "$what не найдена: посещено узлов — ${found.path.size}"
        } else {
            "$what найдена (#${task.id}): посещено узлов — ${found.path.size}"
        }
    }

    private fun refresh() {
        table.refresh()
        tree.redraw()
        selectedTask = selectedTask?.let { service.findById(it.id) }
    }

    private fun handleFailure(title: String, error: Throwable) {
        when (error) {
            is OperationCancelledException -> statusMessage = "Операция отменена, данные не изменены"
            is DataFileException -> showError(title, error.message ?: error.code.description)
            is TaskRepositoryException -> showError(title, error.message ?: UNKNOWN_ERROR)
            else -> showError(title, "${error::class.simpleName}: ${error.message ?: UNKNOWN_ERROR}")
        }
    }

    private fun parseNumber(text: String, range: IntRange): Int? = text.trim().toIntOrNull()?.takeIf { it in range }

    private fun formatName(file: Path): String = FileFormat.of(file)?.title ?: ""

    private companion object {
        const val UNKNOWN_ERROR = "Неизвестная ошибка"
        const val WAIT_FOR_JOB = "Дождитесь окончания фоновой операции"
        const val READ_ONLY = "Эта задача доступна только для чтения"
        const val DATE_FORMAT = "нужен формат ${DisplayDate.PATTERN_HINT}"
    }
}
