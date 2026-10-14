package app.blesk.feature.chats.groups

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R

/**
 * Rows that can be put in order: a long press lifts a row off the column and it follows the
 * finger; past half of a neighbour it takes that place and the neighbour flows into the gap. Let
 * go, and [onMove] gets the row's new index. TalkBack gets «Выше» and «Ниже» instead.
 */
@Composable
internal fun <T> ReorderColumn(
    items: List<T>,
    key: (T) -> String,
    onMove: (id: String, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    row: @Composable (T) -> Unit,
) {
    val haptics = rememberHaptics()
    var order by remember(items) { mutableStateOf(items.map(key)) }
    var lifted by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var start by remember { mutableStateOf(0) }
    val heights = remember { HashMap<String, Int>() }
    val upLabel = stringResource(R.string.channel_menu_up)
    val downLabel = stringResource(R.string.channel_menu_down)
    val byKey = items.associateBy(key)
    Column(modifier) {
        order.mapNotNull(byKey::get).forEach { item ->
            val id = key(item)
            val index = order.indexOf(id)
            val dragged = lifted == id
            val base = Modifier
                .onSizeChanged { heights[id] = it.height }
                .zIndex(if (dragged) 1f else 0f)
                .graphicsLayer {
                    if (dragged) {
                        translationY = offset
                        scaleX = LIFT
                        scaleY = LIFT
                    }
                }
            val draggable = if (!enabled) base else base
                .semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(upLabel) { onMove(id, index - 1); true }.takeIf { index > 0 },
                        CustomAccessibilityAction(downLabel) { onMove(id, index + 1); true }.takeIf { index < order.lastIndex },
                    )
                }
                .pointerInput(id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            lifted = id
                            offset = 0f
                            start = order.indexOf(id)
                            haptics.detach()
                        },
                        onDragEnd = {
                            lifted = null
                            offset = 0f
                            haptics.stick()
                            val to = order.indexOf(id)
                            if (to != start) onMove(id, to)
                        },
                        onDragCancel = {
                            lifted = null
                            offset = 0f
                            order = items.map(key)
                        },
                    ) { change, amount ->
                        change.consume()
                        offset += amount.y
                        val at = order.indexOf(id)
                        val next = order.getOrNull(at + 1)?.let { heights[it] }
                        val previous = order.getOrNull(at - 1)?.let { heights[it] }
                        when {
                            next != null && offset > next / 2f -> {
                                order = order.toMutableList().apply { add(at + 1, removeAt(at)) }
                                offset -= next
                                haptics.tick()
                            }
                            previous != null && offset < -previous / 2f -> {
                                order = order.toMutableList().apply { add(at - 1, removeAt(at)) }
                                offset += previous
                                haptics.tick()
                            }
                        }
                    }
                }
            Column(draggable) { row(item) }
        }
    }
}

private const val LIFT = 1.03f
