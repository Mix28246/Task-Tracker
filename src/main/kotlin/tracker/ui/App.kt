package tracker.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

private val TABLE_BORDER_COLOR = Color(0xFFBDBDBD)

@Composable
fun App(state: AppState, window: Frame) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Toolbar(
                canEdit = state.canEditSelected,
                onLoad = { chooseCsvFile(window, CsvDialogMode.OPEN, state.currentFile)?.let(state::load) },
                onSave = { chooseCsvFile(window, CsvDialogMode.SAVE, state.currentFile)?.let(state::save) },
                onAdd = state::startAdd,
                onEdit = state::startEdit,
            )
            TaskTable(
                tasks = state.tasks,
                selectedId = state.selectedId,
                onSelect = state::select,
                modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, TABLE_BORDER_COLOR),
            )
            StatusBar(state)
        }
    }

    when (val dialog = state.dialog) {
        is DialogRequest.Editor -> TaskEditorDialog(
            request = dialog,
            onSubmit = { form -> state.submit(form, dialog.isNew) },
            onCancel = state::closeDialog,
        )
        is DialogRequest.Error -> ErrorDialog(dialog, onClose = state::closeDialog)
        is DialogRequest.LoadReport -> LoadReportDialog(dialog, onClose = state::closeDialog)
        null -> Unit
    }
}

@Composable
private fun Toolbar(
    canEdit: Boolean,
    onLoad: () -> Unit,
    onSave: () -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onLoad) { Text("Загрузить CSV") }
        Button(onClick = onSave) { Text("Сохранить CSV") }
        Spacer(Modifier.width(16.dp))
        Button(onClick = onAdd) { Text("Добавить") }

        Button(onClick = onEdit, enabled = canEdit) { Text("Изменить") }
    }
}

@Composable
private fun StatusBar(state: AppState) {
    val selected = state.selectedTask
    val selectionInfo = when {
        selected == null -> "Задача не выбрана"
        state.canEditSelected -> "Выбрана задача #${selected.id}"
        else -> "Выбрана задача #${selected.id} — только для чтения"
    }
    val fileInfo = state.currentFile?.let { "Файл: $it" } ?: "Файл не выбран"

    Column {
        Text("${state.statusMessage}  ·  Всего задач: ${state.tasks.size}  ·  $selectionInfo")
        Text(fileInfo, style = MaterialTheme.typography.caption, color = Color.Gray)
    }
}

private const val CSV_EXTENSION = ".csv"
private const val DEFAULT_FILE_NAME = "tasks.csv"

enum class CsvDialogMode { OPEN, SAVE }

fun chooseCsvFile(parent: Frame, mode: CsvDialogMode, current: Path?): Path? {
    val dialog = when (mode) {
        CsvDialogMode.OPEN -> FileDialog(parent, "Загрузить задачи из CSV", FileDialog.LOAD)
        CsvDialogMode.SAVE -> FileDialog(parent, "Сохранить задачи в CSV", FileDialog.SAVE)
    }
    dialog.directory = current?.parent?.toString() ?: System.getProperty("user.dir")

    dialog.file = if (mode == CsvDialogMode.OPEN) "*$CSV_EXTENSION" else current?.fileName?.toString() ?: DEFAULT_FILE_NAME
    dialog.setFilenameFilter { _, name -> name.endsWith(CSV_EXTENSION, ignoreCase = true) }
    dialog.isVisible = true

    val directory = dialog.directory ?: return null
    val fileName = dialog.file ?: return null
    val withExtension =
        if (mode == CsvDialogMode.SAVE && !fileName.endsWith(CSV_EXTENSION, ignoreCase = true)) fileName + CSV_EXTENSION
        else fileName
    return Path.of(directory, withExtension)
}
