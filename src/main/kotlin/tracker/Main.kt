package tracker

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import tracker.service.TaskService
import tracker.ui.App
import tracker.ui.AppState
import tracker.ui.BackgroundRunner

fun main() = application {
    val runner = remember { BackgroundRunner() }
    val state = remember { AppState(TaskService(), runner) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Таск-трекер",
        state = rememberWindowState(size = DpSize(1320.dp, 820.dp)),
    ) {
        MaterialTheme {
            App(state, window)
        }
    }
}
