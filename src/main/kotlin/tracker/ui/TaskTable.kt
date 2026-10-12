package tracker.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tracker.model.ArchivedTask
import tracker.model.Task
import tracker.model.UrgentTask

private data class TableColumn(val title: String, val weight: Float)

private val COLUMNS = listOf(
    TableColumn("ID", 0.5f),
    TableColumn("Тип", 1.3f),
    TableColumn("Название", 2.6f),
    TableColumn("Исполнитель", 1.5f),
    TableColumn("Срок", 1.0f),
    TableColumn("Важность", 0.8f),
    TableColumn("Дежурный", 1.5f),
)

private val READ_ONLY_COLOR = Color.Gray
private val OVERDUE_COLOR = Color(0xFFC62828)
private val HEADER_BACKGROUND = Color(0xFFEEEEEE)

@Composable
fun TaskTable(
    tasks: List<Task>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().background(HEADER_BACKGROUND).padding(vertical = 8.dp)) {
            COLUMNS.forEach { Cell(it.title, it.weight, fontWeight = FontWeight.Bold) }
        }
        Divider()

        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Задач нет. Загрузите CSV-файл или нажмите «Добавить».", color = READ_ONLY_COLOR)
            }
        } else {
            TaskList(tasks, selectedId, onSelect)
        }
    }
}

@Composable
private fun TaskList(tasks: List<Task>, selectedId: Int?, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
            items(tasks, key = { it.id }) { task ->
                TaskRow(task, isSelected = task.id == selectedId, onClick = { onSelect(task.id) })
                Divider()
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
    val background = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.18f) else Color.Transparent
    val isReadOnly = task is ArchivedTask
    val textColor = if (isReadOnly) READ_ONLY_COLOR else Color.Unspecified
    val style = if (isReadOnly) FontStyle.Italic else FontStyle.Normal

    val typeLabel = if (isReadOnly) "${task.type.displayName} (только чтение)" else task.type.displayName
    val deadlineColor = if (!isReadOnly && task.isOverdue()) OVERDUE_COLOR else textColor

    Row(
        Modifier.fillMaxWidth().background(background).clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Cell(task.id.toString(), COLUMNS[0].weight, textColor, style)
        Cell(typeLabel, COLUMNS[1].weight, textColor, style)
        Cell(task.title, COLUMNS[2].weight, textColor, style)
        Cell(task.assignee, COLUMNS[3].weight, textColor, style)
        Cell(task.deadline.toString(), COLUMNS[4].weight, deadlineColor, style)
        Cell(task.importance.toString(), COLUMNS[5].weight, textColor, style)
        Cell((task as? UrgentTask)?.dutyPerson ?: "—", COLUMNS[6].weight, textColor, style)
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
