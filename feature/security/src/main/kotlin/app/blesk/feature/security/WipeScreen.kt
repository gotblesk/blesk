package app.blesk.feature.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.haptics.rememberHaptics
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun WipeScreen(onWipe: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScrollScreen(title = stringResource(R.string.wipe_title), onBack = onBack, modifier = modifier) {
        Text(
            text = stringResource(R.string.wipe_text),
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 16.dp),
        )
        SlideToConfirm(
            label = stringResource(R.string.wipe_slide),
            actionLabel = stringResource(R.string.wipe_action),
            onConfirm = onWipe,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 24.dp),
        )
    }
}

/**
 * A slider that must be dragged all the way to the end. Destructive actions use it instead of
 * a button so a stray tap can never trigger them. TalkBack gets an explicit action.
 */
@Composable
fun SlideToConfirm(label: String, actionLabel: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val knob = 56.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(Blesk.colors.surface)
            .semantics {
                contentDescription = label
                customActions = listOf(CustomAccessibilityAction(actionLabel) { onConfirm(); true })
            },
    ) {
        val travel = with(density) { (maxWidth - knob - 8.dp).toPx() }
        Text(
            text = label,
            style = Blesk.type.bodyStrong,
            color = Blesk.colors.danger,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = 1f - (offset.value / travel).coerceIn(0f, 1f) },
        )
        Box(
            modifier = Modifier
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
                                    haptics.event()
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
            Icon(BleskIcons.Trash, contentDescription = null, tint = Blesk.colors.background)
        }
    }
}

private const val CONFIRM_AT = 0.95f
