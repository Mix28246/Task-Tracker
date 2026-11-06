package tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.awt.Frame
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import tracker.io.FileFormat
import tracker.model.Editable

private const val DEFAULT_GENERATE_COUNT = "10000"
private const val PERCENT = 100f
private val JOB_LABEL_WIDTH = 380.dp
private val COUNT_FIELD_WIDTH = 150.dp
private val TOOLBAR_GAP = 16.dp

@Composable
fun App(state: AppState, window: Frame) {
    val busy = state.runner.isBusy
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Toolbar(
                busy = busy,
                canEdit = state.canEditSelected,
                onImport = { chooseFile(window, FileDialogMode.IMPORT, state.currentFile)?.let(state::importFile) },
                onExport = { chooseFile(window, FileDialogMode.EXPORT, state.currentFile)?.let(state::exportFile) },
                onAdd = state::startAdd,
                onEdit = state::startEdit,
                onDelete = state::requestDelete,
                onGenerate = state::generate,
            )
            val progress = state.runner.progress
            if (progress != null) JobBar(progress, onCancel = state::cancelJob)
            TabRow(selectedTabIndex = state.screen.ordinal) {
                Screen.entries.forEach { screen ->
                    Tab(selected = state.screen == screen, onClick = { state.open(screen) }, text = { Text(screen.title) })
                }
            }
            val content = Modifier.weight(1f).fillMaxWidth()
            when (state.screen) {
                Screen.TABLE -> TableScreen(state, content)
                Screen.TREE -> TreeScreen(state, content)
            }
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
        is DialogRequest.ConfirmDelete -> ConfirmDeleteDialog(
            request = dialog,
            onConfirm = { state.confirmDelete(dialog.task) },
            onCancel = state::closeDialog,
        )
        null -> Unit
    }
}

@Composable
private fun Toolbar(
    busy: Boolean,
    canEdit: Boolean,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onGenerate: (String) -> Unit,
) {
    var count by remember { mutableStateOf(DEFAULT_GENERATE_COUNT) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onImport, enabled = !busy) { Text("Импорт…") }
        Button(onClick = onExport, enabled = !busy) { Text("Экспорт…") }
        Spacer(Modifier.width(TOOLBAR_GAP))
        Button(onClick = onAdd, enabled = !busy) { Text("Добавить") }
        Button(onClick = onEdit, enabled = canEdit) { Text("Изменить") }
        Button(onClick = onDelete, enabled = canEdit) { Text("Удалить") }
        Spacer(Modifier.width(TOOLBAR_GAP))
        OutlinedTextField(
            value = count,
            onValueChange = { count = it },
            label = { Text("Сколько задач") },
            singleLine = true,
            modifier = Modifier.width(COUNT_FIELD_WIDTH),
        )
        Button(onClick = { onGenerate(count) }, enabled = !busy) { Text("Сгенерировать") }
    }
}

@Composable
private fun JobBar(progress: JobProgress, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val stage = if (progress.stage.isEmpty()) "" else " — ${progress.stage}"
        Text("${progress.title}$stage: ${progress.percent}%", modifier = Modifier.width(JOB_LABEL_WIDTH))
        LinearProgressIndicator(progress = progress.percent / PERCENT, modifier = Modifier.weight(1f))
        if (progress.cancellable) OutlinedButton(onClick = onCancel) { Text("Отмена") }
    }
}

@Composable
private fun StatusBar(state: AppState) {
    val selected = state.selectedTask
    val selectionInfo = when {
        selected == null -> "Задача не выбрана"
        selected is Editable -> "Выбрана задача #${selected.id}"
        else -> "Выбрана задача #${selected.id} — только для чтения"
    }
    val fileInfo = state.currentFile?.let { "Файл: $it" } ?: "Файл не выбран"

    Column {
        Text("${state.statusMessage}  ·  Всего задач: ${state.table.totalCount}  ·  $selectionInfo")
        Text(fileInfo, style = MaterialTheme.typography.caption, color = Color.Gray)
    }
}

private const val DEFAULT_FILE_NAME = "tasks"

enum class FileDialogMode { IMPORT, EXPORT }

fun chooseFile(parent: Frame, mode: FileDialogMode, current: Path?): Path? {
    val startDirectory = current?.parent?.toFile() ?: File(System.getProperty("user.dir"))
    val chooser = JFileChooser(startDirectory)
    chooser.isAcceptAllFileFilterUsed = false
    val filters = FileFormat.entries.associateWith { format ->
        FileNameExtensionFilter("${format.title} (*.${format.extension})", format.extension)
    }
    val answer = when (mode) {
        FileDialogMode.IMPORT -> {
            val extensions = FileFormat.entries.map { it.extension }.toTypedArray()
            chooser.addChoosableFileFilter(FileNameExtensionFilter("Все поддерживаемые (CSV, JSON, BIN)", *extensions))
            filters.values.forEach(chooser::addChoosableFileFilter)
            chooser.dialogTitle = "Импорт задач"
            chooser.showOpenDialog(parent)
        }
        FileDialogMode.EXPORT -> {
            filters.values.forEach(chooser::addChoosableFileFilter)
            val format = current?.let(FileFormat::of) ?: FileFormat.CSV
            chooser.fileFilter = filters.getValue(format)
            chooser.selectedFile = File(startDirectory, current?.fileName?.toString() ?: "$DEFAULT_FILE_NAME.${format.extension}")
            chooser.dialogTitle = "Экспорт задач"
            chooser.showSaveDialog(parent)
        }
    }
    if (answer != JFileChooser.APPROVE_OPTION) return null
    val file = chooser.selectedFile?.toPath() ?: return null
    if (mode == FileDialogMode.IMPORT) return file
    val chosen = filters.entries.firstOrNull { it.value == chooser.fileFilter }?.key ?: FileFormat.CSV
    val name = file.fileName.toString()
    val baseName = FileFormat.of(file)?.let { name.dropLast(it.extension.length + 1) } ?: name
    return file.resolveSibling("$baseName.${chosen.extension}")
}
