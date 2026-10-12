package tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import tracker.model.TaskRules
import tracker.model.TaskType

@Composable
fun TaskEditorDialog(
    request: DialogRequest.Editor,
    onSubmit: (TaskForm) -> List<String>,
    onCancel: () -> Unit,
) {
    var form by remember(request) { mutableStateOf(request.form) }
    var errors by remember(request) { mutableStateOf(emptyList<String>()) }

    val title = if (request.isNew) "Новая задача" else "Изменить задачу #${form.id}"

    DialogWindow(
        onCloseRequest = onCancel,
        state = rememberDialogState(size = DpSize(480.dp, 640.dp)),
        title = title,
        resizable = false,
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("ID: ${form.id}", fontWeight = FontWeight.Bold)

                if (request.isNew) {
                    Text("Тип задачи:")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TaskForm.CREATABLE_TYPES.forEach { type ->
                            TypeOption(type, selected = form.type == type) { form = form.copy(type = type) }
                        }
                    }
                } else {
                    Text("Тип задачи: ${form.type.displayName}")
                }

                FormField("Название", form.title) { form = form.copy(title = it) }
                FormField("Исполнитель", form.assignee) { form = form.copy(assignee = it) }
                FormField("Срок (ГГГГ-ММ-ДД)", form.deadline) { form = form.copy(deadline = it) }
                FormField(
                    "Важность (${TaskRules.MIN_IMPORTANCE}–${TaskRules.MAX_IMPORTANCE})",
                    form.importance,
                ) { form = form.copy(importance = it) }
                if (form.type == TaskType.URGENT) {
                    FormField("Дежурный", form.dutyPerson) { form = form.copy(dutyPerson = it) }
                }

                errors.forEach { Text("• $it", color = MaterialTheme.colors.error) }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    OutlinedButton(onClick = onCancel) { Text("Отмена") }
                    Button(onClick = { errors = onSubmit(form) }) { Text("Сохранить") }
                }
            }
        }
    }
}

@Composable
private fun TypeOption(type: TaskType, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(type.displayName)
    }
}

@Composable
private fun FormField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ErrorDialog(request: DialogRequest.Error, onClose: () -> Unit) {
    DialogWindow(
        onCloseRequest = onClose,
        state = rememberDialogState(size = DpSize(460.dp, 260.dp)),
        title = request.title,
        resizable = false,
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(request.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.error)
                Spacer(Modifier.height(8.dp))
                Text(request.text, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onClose) { Text("ОК") }
                }
            }
        }
    }
}

@Composable
fun LoadReportDialog(request: DialogRequest.LoadReport, onClose: () -> Unit) {
    DialogWindow(
        onCloseRequest = onClose,
        state = rememberDialogState(size = DpSize(640.dp, 420.dp)),
        title = "Отчёт о загрузке",
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    "Загружено задач: ${request.loadedCount}. Пропущено битых строк: ${request.skipped.size}",
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(request.skipped) { error ->
                        Text(error.message ?: error.code.name, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onClose) { Text("ОК") }
                }
            }
        }
    }
}
