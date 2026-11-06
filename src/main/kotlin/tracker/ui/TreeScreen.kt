package tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import tracker.model.ArchivedTask
import tracker.model.DisplayDate
import tracker.model.Task
import tracker.model.TaskKey
import tracker.model.display
import tracker.service.TaskService
import tracker.store.ArrayBuffer
import tracker.store.NO_PARENT
import tracker.store.SnapshotNode
import tracker.store.TreeSnapshot

private const val HIDDEN_LABEL_GAP = 2f
private const val PATH_STROKE = 4f
private const val EDGE_STROKE = 1.5f
private const val PATH_ALPHA = 0.12f
private const val FOCUS_ALPHA = 0.35f
private val SEARCH_FIELD_WIDTH = 140.dp
private val NODE_TEXT_SIZE = 10.sp
private val HIDDEN_TEXT_SIZE = 10.sp
private val PATH_BORDER = 2.dp
private val NODE_BORDER = 1.dp
private val NODE_PADDING_H = 4.dp
private val NODE_PADDING_V = 2.dp
private val EDGE_COLOR = Color(0xFF9E9E9E)
private val NODE_BACKGROUND = Color(0xFFFFFFFF)
private val ARCHIVED_COLOR = Color(0xFF757575)

class TreeViewState(private val service: TaskService) {
    var highlightedPath: ArrayBuffer<TaskKey> by mutableStateOf(ArrayBuffer())
        private set

    var focusedKey: TaskKey? by mutableStateOf(null)
        private set

    var snapshot: TreeSnapshot<TaskKey, Task> by mutableStateOf(service.treeSnapshot(VISIBLE_LEVELS - 1))
        private set

    fun highlight(path: ArrayBuffer<TaskKey>, focus: TaskKey?) {
        highlightedPath = path
        focusedKey = focus
        redraw()
    }

    fun clearHighlight() {
        highlight(ArrayBuffer(), null)
    }

    fun redraw() {
        val path = highlightedPath
        snapshot = service.treeSnapshot(VISIBLE_LEVELS - 1) { key -> key in path }
    }

    companion object {
        const val VISIBLE_LEVELS = 5
    }
}

class TreeLayout(val centerX: FloatArray, val top: FloatArray, val width: Float, val height: Float)

object TreeLayoutCalculator {
    const val NODE_WIDTH = 72f
    const val NODE_HEIGHT = 50f
    const val SLOT = 78f
    const val LEVEL_STEP = 78f
    const val MARGIN = 16f
    private const val MIN_OFFSET = 24f
    private const val MAX_SHIFT = 30

    fun <K, T> layout(nodes: ArrayBuffer<SnapshotNode<K, T>>, visibleLevels: Int): TreeLayout {
        val treeWidth = SLOT * (1 shl (visibleLevels - 1))
        val centerX = FloatArray(nodes.size) { Float.NaN }
        val top = FloatArray(nodes.size)
        var deepest = 0
        for (index in 0 until nodes.size) {
            place(nodes, index, centerX, treeWidth)
            val depth = nodes[index].depth
            top[index] = MARGIN + depth * LEVEL_STEP
            deepest = maxOf(deepest, depth)
        }
        var minX = Float.MAX_VALUE
        var maxX = 0f
        for (x in centerX) {
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
        }
        val shift = if (nodes.isEmpty()) 0f else MARGIN + NODE_WIDTH / 2 - minOf(minX, treeWidth / 2)
        for (index in centerX.indices) centerX[index] += shift
        val width = maxOf(treeWidth + 2 * MARGIN, maxX + shift + NODE_WIDTH / 2 + MARGIN)
        val height = 2 * MARGIN + (deepest + 1) * LEVEL_STEP
        return TreeLayout(centerX, top, width, height)
    }

    private fun <K, T> place(
        nodes: ArrayBuffer<SnapshotNode<K, T>>,
        index: Int,
        centerX: FloatArray,
        treeWidth: Float,
    ): Float {
        if (!centerX[index].isNaN()) return centerX[index]
        val node = nodes[index]
        val x = if (node.parent == NO_PARENT) {
            treeWidth / 2
        } else {
            val parent = nodes[node.parent]
            val parentX = place(nodes, node.parent, centerX, treeWidth)
            val offset = maxOf(treeWidth / (1 shl minOf(node.depth + 1, MAX_SHIFT)), MIN_OFFSET)
            if (node.column < parent.column) parentX - offset else parentX + offset
        }
        centerX[index] = x
        return x
    }
}

@Composable
fun TreeScreen(state: AppState, modifier: Modifier = Modifier) {
    var dateText by remember { mutableStateOf(LocalDate.now().display()) }
    val tree = state.tree
    val snapshot = tree.snapshot

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text("Срок (${DisplayDate.PATTERN_HINT})") },
                singleLine = true,
                modifier = Modifier.width(SEARCH_FIELD_WIDTH),
            )
            Button(
                onClick = { state.findByDeadline(dateText) },
                enabled = snapshot.totalSize > 0 && !state.runner.isBusy,
            ) { Text("Найти в дереве") }
        }
        Text(
            "Узлов: ${snapshot.totalSize}, высота: ${snapshot.height}. В узле: срок (ключ) и важность (приоритет кучи). " +
                "Показаны верхние ${TreeViewState.VISIBLE_LEVELS} уровней; «+N ниже» — сколько узлов скрыто. " +
                "Синим выделен путь последнего поиска, вставки или удаления.",
            style = MaterialTheme.typography.caption,
        )
        if (snapshot.nodes.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Дерево пустое. Загрузите CSV, сгенерируйте или добавьте задачи.", color = Color.Gray)
            }
        } else {
            TreeCanvas(
                snapshot = snapshot,
                highlighted = tree.highlightedPath,
                focused = tree.focusedKey,
                modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, EDGE_COLOR),
            )
        }
    }
}

@Composable
private fun TreeCanvas(
    snapshot: TreeSnapshot<TaskKey, Task>,
    highlighted: ArrayBuffer<TaskKey>,
    focused: TaskKey?,
    modifier: Modifier,
) {
    val nodes = snapshot.nodes
    val layout = remember(snapshot) { TreeLayoutCalculator.layout(nodes, TreeViewState.VISIBLE_LEVELS) }
    val pathColor = MaterialTheme.colors.primary

    Box(modifier.horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        Box(Modifier.size(layout.width.dp, layout.height.dp)) {
            Canvas(Modifier.matchParentSize()) {
                for (index in 0 until nodes.size) {
                    val node = nodes[index]
                    if (node.parent == NO_PARENT) continue
                    val parent = nodes[node.parent]
                    val onPath = node.key in highlighted && parent.key in highlighted
                    drawLine(
                        color = if (onPath) pathColor else EDGE_COLOR,
                        start = Offset(
                            layout.centerX[node.parent].dp.toPx(),
                            (layout.top[node.parent] + TreeLayoutCalculator.NODE_HEIGHT).dp.toPx(),
                        ),
                        end = Offset(layout.centerX[index].dp.toPx(), layout.top[index].dp.toPx()),
                        strokeWidth = if (onPath) PATH_STROKE else EDGE_STROKE,
                    )
                }
            }
            for (index in 0 until nodes.size) {
                val node = nodes[index]
                TreeNode(
                    node = node,
                    left = layout.centerX[index] - TreeLayoutCalculator.NODE_WIDTH / 2,
                    top = layout.top[index],
                    onPath = node.key in highlighted,
                    isFocused = node.key == focused,
                )
            }
        }
    }
}

@Composable
private fun TreeNode(node: SnapshotNode<TaskKey, Task>, left: Float, top: Float, onPath: Boolean, isFocused: Boolean) {
    val task = node.value
    val accent = MaterialTheme.colors.primary
    val background = when {
        isFocused -> accent.copy(alpha = FOCUS_ALPHA)
        onPath -> accent.copy(alpha = PATH_ALPHA)
        else -> NODE_BACKGROUND
    }
    val archived = task is ArchivedTask
    val textColor = if (archived) ARCHIVED_COLOR else Color.Unspecified
    val style = if (archived) FontStyle.Italic else FontStyle.Normal

    Box(
        Modifier
            .offset(left.dp, top.dp)
            .size(TreeLayoutCalculator.NODE_WIDTH.dp, TreeLayoutCalculator.NODE_HEIGHT.dp)
            .background(background)
            .border(if (onPath) PATH_BORDER else NODE_BORDER, if (onPath) accent else EDGE_COLOR),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = NODE_PADDING_H, vertical = NODE_PADDING_V),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                task.deadline.display(),
                fontSize = NODE_TEXT_SIZE,
                fontWeight = FontWeight.Bold,
                fontStyle = style,
                color = textColor,
                maxLines = 1,
            )
            Text("важность ${task.importance}", fontSize = NODE_TEXT_SIZE, fontStyle = style, color = textColor, maxLines = 1)
            Text("#${task.id}", fontSize = NODE_TEXT_SIZE, fontStyle = style, color = Color.Gray, maxLines = 1)
        }
    }
    if (node.hiddenBelow > 0) {
        Text(
            "+${node.hiddenBelow} ниже",
            modifier = Modifier.offset(left.dp, (top + TreeLayoutCalculator.NODE_HEIGHT + HIDDEN_LABEL_GAP).dp),
            fontSize = HIDDEN_TEXT_SIZE,
            color = Color.Gray,
            maxLines = 1,
        )
    }
}
