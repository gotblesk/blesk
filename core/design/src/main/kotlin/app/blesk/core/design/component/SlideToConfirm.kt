package app.blesk.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A slider that must be dragged all the way to the end. Destructive actions use it instead of
 * a button so a stray tap can never trigger them. TalkBack gets an explicit action. [icon] rides
 * on the knob: what the action does, a bin for deleting. Once confirmed it stays at the end; a
 * caller whose action may still be called off shows a fresh one under a new `key`.
 */
@Composable
fun SlideToConfirm(
    label: String,
    actionLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = BleskIcons.Trash,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val knob = 56.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(CircleShape)
            .background(Blesk.colors.surface)
            .semantics {
                contentDescription = label
                customActions = listOf(CustomAccessibilityAction(actionLabel) { onConfirm(); true })
            },
    ) {
        val travel = with(density) { (maxWidth - knob - 8.dp).toPx() }
        // Kept clear of the knob on both sides, so it stays centred and a large font wraps it instead of hiding it.
        Text(
            text = label,
            style = Blesk.type.bodyStrong,
            color = Blesk.colors.danger,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = knob + 8.dp, vertical = 8.dp)
                .graphicsLayer { alpha = 1f - (offset.value / travel).coerceIn(0f, 1f) },
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(4.dp)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(knob)
                .clip(CircleShape)
                .background(Blesk.colors.danger)
                .pointerInput(travel) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value >= travel * CONFIRM_AT && !done) {
                                    done = true
                                    offset.animateTo(travel)
                                    haptics.heavy()
                                    onConfirm()
                                } else {
                                    offset.animateTo(0f, spring(dampingRatio = 0.75f))
                                }
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + amount).coerceIn(0f, travel)) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Blesk.colors.background)
        }
    }
}

private const val CONFIRM_AT = 0.95f
