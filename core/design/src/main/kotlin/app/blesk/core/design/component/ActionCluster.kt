package app.blesk.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk

/** One capsule of an [ActionCluster]. */
class ClusterAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * Capsules stuck together into one drop. The one under the finger shrinks and comes away from
 * the others before its action runs.
 */
@Composable
fun ActionCluster(actions: List<ClusterAction>, modifier: Modifier = Modifier) {
    val presses = actions.map { remember { MutableInteractionSource() } }
    val shrink = presses.map { source ->
        val pressed by source.collectIsPressedAsState()
        val value by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, Blesk.motion.press(), label = "press")
        value
    }
    val gap = 6.dp
    Row(
        modifier
            .fillMaxWidth()
            .metaballs(Blesk.colors.surface, smoothness = 12.dp) {
                val g = gap.toPx()
                val w = (size.width - g * (actions.size - 1)) / actions.size
                actions.indices.map { i ->
                    val s = shrink[i]
                    val cx = i * (w + g) + w / 2f
                    val half = w / 2f * s
                    val h = size.height * s
                    Blob(Rect(cx - half, (size.height - h) / 2f, cx + half, (size.height + h) / 2f), h / 2f)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        actions.forEachIndexed { index, action ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .clickable(interactionSource = presses[index], indication = null, role = Role.Button, onClick = action.onClick)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(action.icon, contentDescription = null, tint = Blesk.colors.textPrimary)
                // Five capsules leave little room, so their labels are set a little smaller.
                val caption = Blesk.type.caption
                val style = if (actions.size > CROWDED) caption.copy(fontSize = caption.fontSize * CROWDED_SCALE) else caption
                Text(action.label, style = style, color = Blesk.colors.textPrimary, maxLines = 1)
            }
        }
    }
}

private const val PRESSED_SCALE = 0.92f
private const val CROWDED = 4
private const val CROWDED_SCALE = 0.88f
