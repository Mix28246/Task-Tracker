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

fun main() = application {
    val state = remember { AppState(TaskService()) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Таск-трекер",
        state = rememberWindowState(size = DpSize(1150.dp, 680.dp)),
    ) {
        MaterialTheme {
            App(state, window)
        }
    }
}
