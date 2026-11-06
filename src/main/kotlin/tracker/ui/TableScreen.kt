package tracker.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import tracker.model.ArchivedTask
import tracker.model.DisplayDate
import tracker.model.Task
import tracker.model.UrgentTask
import tracker.model.display
import tracker.service.TaskService
import tracker.store.ArrayBuffer

private val TABLE_BORDER_COLOR = Color(0xFFBDBDBD)
private const val DEFAULT_WINDOW_DAYS = 30L
private const val DEFAULT_TOP_N = "10"
private val FILTER_GAP = 16.dp
private val DATE_FIELD_WIDTH = 170.dp
private val N_FIELD_WIDTH = 90.dp
private val READ_ONLY_COLOR = Color.Gray
private val OVERDUE_COLOR = Color(0xFFC62828)
private val HEADER_BACKGROUND = Color(0xFFEEEEEE)
private const val SELECTED_ALPHA = 0.18f
private const val NO_DUTY = "—"

sealed interface TaskFilter {
    data class Range(val from: LocalDate, val to: LocalDate) : TaskFilter

    data class Top(val n: Int) : TaskFilter
}

class TableViewState(private val service: TaskService) {
    var rowCount: Int by mutableStateOf(0)
        private set

    var version: Int by mutableStateOf(0)
        private set

    var totalCount: Int by mutableStateOf(0)
        private set

    var filter: TaskFilter? by mutableStateOf(null)
        private set

    private var filteredRows: ArrayBuffer<Task>? = null

    fun rowAt(index: Int): Task? {
        val rows = filteredRows
        return if (rows != null) rows.getOrNull(index) else service.taskAt(index)
    }

    fun applyFilter(newFilter: TaskFilter?) {
        filter = newFilter
        refresh()
    }

    fun refresh() {
        val total = service.size()
        val rows = when (val active = filter) {
            null -> null
            is TaskFilter.Range -> service.range(active.from, active.to)
            is TaskFilter.Top -> service.mostImportant(active.n)
        }
        filteredRows = rows
        rowCount = rows?.size ?: total
        totalCount = total
        version++
    }
}

@Composable
fun TableScreen(state: AppState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterBar(state)
        TaskTable(
            rowCount = state.table.rowCount,
            version = state.table.version,
            rowAt = state.table::rowAt,
            selectedId = state.selectedId,
            onSelect = state::select,
            modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, TABLE_BORDER_COLOR),
        )
    }
}

@Composable
private fun FilterBar(state: AppState) {
    var from by remember { mutableStateOf(LocalDate.now().display()) }
    var to by remember { mutableStateOf(LocalDate.now().plusDays(DEFAULT_WINDOW_DAYS).display()) }
    var n by remember { mutableStateOf(DEFAULT_TOP_N) }
    val idle = !state.runner.isBusy

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = from,
            onValueChange = { from = it },
            label = { Text("Срок с (${DisplayDate.PATTERN_HINT})") },
            singleLine = true,
            modifier = Modifier.width(DATE_FIELD_WIDTH),
        )
        OutlinedTextField(
            value = to,
            onValueChange = { to = it },
            label = { Text("по") },
            singleLine = true,
            modifier = Modifier.width(DATE_FIELD_WIDTH),
        )
        Button(onClick = { state.applyRange(from, to) }, enabled = idle) { Text("Задачи в интервале") }
        Spacer(Modifier.width(FILTER_GAP))
        OutlinedTextField(
            value = n,
            onValueChange = { n = it },
            label = { Text("N") },
            singleLine = true,
            modifier = Modifier.width(N_FIELD_WIDTH),
        )
        Button(onClick = { state.applyTop(n) }, enabled = idle) { Text("N самых важных") }
        Spacer(Modifier.width(FILTER_GAP))
        OutlinedButton(onClick = state::clearFilter, enabled = idle && state.table.filter != null) { Text("Показать все") }
    }

    val table = state.table
    val description = when (val active = table.filter) {
        null -> "Показаны все задачи в порядке ключей дерева: по сроку, при равном сроке — по ID"
        is TaskFilter.Range -> "Фильтр: срок с ${active.from.display()} по ${active.to.display()} — ${table.rowCount} из ${table.totalCount}"
        is TaskFilter.Top ->
            "Фильтр: ${active.n} самых важных (по убыванию важности, при равной — по сроку) — " +
                "${table.rowCount} из ${table.totalCount}"
    }
    Text(description, style = MaterialTheme.typography.caption, color = Color.Gray)
}

private enum class TableColumn(val title: String, val weight: Float) {
    ID("ID", 0.5f),
    TYPE("Тип", 1.3f),
    TITLE("Название", 2.6f),
    ASSIGNEE("Исполнитель", 1.5f),
    DEADLINE("Срок", 1.0f),
    IMPORTANCE("Важность", 0.8f),
    DUTY("Дежурный", 1.5f),
}

@Composable
fun TaskTable(
    rowCount: Int,
    version: Int,
    rowAt: (Int) -> Task?,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().background(HEADER_BACKGROUND).padding(vertical = 8.dp)) {
            TableColumn.entries.forEach { Cell(it.title, it.weight, fontWeight = FontWeight.Bold) }
        }
        Divider()

        if (rowCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Задач нет. Загрузите CSV-файл, сгенерируйте или нажмите «Добавить».", color = READ_ONLY_COLOR)
            }
        } else {
            TaskList(rowCount, version, rowAt, selectedId, onSelect)
        }
    }
}

@Composable
private fun TaskList(
    rowCount: Int,
    version: Int,
    rowAt: (Int) -> Task?,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
            items(count = rowCount) { index ->
                val task = remember(version, index) { rowAt(index) }
                if (task != null) {
                    TaskRow(task, isSelected = task.id == selectedId, onClick = { onSelect(task.id) })
                    Divider()
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun TaskRow(task: Task, isSelected: Boolean, onClick: () -> Unit) {
    val background = if (isSelected) MaterialTheme.colors.primary.copy(alpha = SELECTED_ALPHA) else Color.Transparent
    val isReadOnly = task is ArchivedTask
    val textColor = if (isReadOnly) READ_ONLY_COLOR else Color.Unspecified
    val style = if (isReadOnly) FontStyle.Italic else FontStyle.Normal

    val typeLabel = if (isReadOnly) "${task.type.displayName} (только чтение)" else task.type.displayName
    val deadlineColor = if (!isReadOnly && task.isOverdue()) OVERDUE_COLOR else textColor

    Row(
        Modifier.fillMaxWidth().background(background).clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Cell(task.id.toString(), TableColumn.ID.weight, textColor, style)
        Cell(typeLabel, TableColumn.TYPE.weight, textColor, style)
        Cell(task.title, TableColumn.TITLE.weight, textColor, style)
        Cell(task.assignee, TableColumn.ASSIGNEE.weight, textColor, style)
        Cell(task.deadline.display(), TableColumn.DEADLINE.weight, deadlineColor, style)
        Cell(task.importance.toString(), TableColumn.IMPORTANCE.weight, textColor, style)
        Cell((task as? UrgentTask)?.dutyPerson ?: NO_DUTY, TableColumn.DUTY.weight, textColor, style)
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    color: Color = Color.Unspecified,
    fontStyle: FontStyle = FontStyle.Normal,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight).padding(horizontal = 8.dp),
        color = color,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
