package app.blesk.feature.chats

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Icon
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What a row does when swiped far enough to one side. */
internal class SwipeAction(val icon: ImageVector, val label: String, val run: () -> Unit)

/**
 * A row that follows the finger sideways; far enough to the [left] or [right] does that side's
 * action, and the row slides back. TalkBack gets the same as named actions.
 */
@Composable
internal fun SideSwipe(
    left: SwipeAction?,
    right: SwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (left == null && right == null) {
        Box(modifier) { content() }
        return
    }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val threshold = with(LocalDensity.current) { SwipeThreshold.toPx() }
    Box(
        modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOfNotNull(left, right).map { action -> CustomAccessibilityAction(action.label) { action.run(); true } }
            },
    ) {
        val progress = (abs(offset.value) / threshold).coerceIn(0f, 1f)
        val shown = if (offset.value > 0) right else left
        if (offset.value != 0f && shown != null) {
            Row(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
                    .background(Blesk.colors.surface.copy(alpha = progress)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (offset.value > 0) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    icon = shown.icon,
                    contentDescription = null,
                    tint = Blesk.colors.textSecondary.copy(alpha = progress),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .pointerInput(left, right) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val value = offset.value
                            when {
                                value <= -threshold && left != null -> left.run()
                                value >= threshold && right != null -> right.run()
                            }
                            if (abs(value) >= threshold) haptics.tick()
                            scope.launch { offset.animateTo(0f) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, delta ->
                        val next = offset.value + delta
                        val allowed = (next < 0 && left != null) || (next > 0 && right != null)
                        if (allowed || next == 0f) {
                            change.consume()
                            scope.launch { offset.snapTo(next.coerceIn(-threshold * OVERSHOOT, threshold * OVERSHOOT)) }
                        }
                    }
                },
        ) { content() }
    }
}

private val SwipeThreshold = 96.dp
private const val OVERSHOOT = 1.4f
