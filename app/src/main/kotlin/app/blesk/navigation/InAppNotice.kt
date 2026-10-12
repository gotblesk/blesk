package app.blesk.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.InAppNote
import app.blesk.R
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Text
import app.blesk.core.design.glass.lens
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A message for another chat while the app is open: a capsule flows out of the top lens with
 * who wrote and how it starts. Swiping it up puts it away, a press opens that chat. One at a
 * time; a newer one takes its place.
 */
@Composable
internal fun InAppNotice(note: InAppNote?, onOpen: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(note) }
    if (note != null) shown = note
    val motion = Blesk.motion
    val haptics = rememberHaptics()
    LaunchedEffect(note) {
        if (note == null) return@LaunchedEffect
        haptics.tick()
        delay(SHOW_MILLIS)
        onDismiss()
    }
    AnimatedVisibility(
        visible = note != null,
        enter = slideInVertically(motion.settle()) { -it } + scaleIn(motion.settle(), initialScale = 0.6f) + fadeIn(motion.press()),
        exit = slideOutVertically(motion.transition()) { -it } + fadeOut(motion.transition()),
        modifier = modifier,
    ) {
        val current = shown ?: return@AnimatedVisibility
        val drag = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val limit = with(LocalDensity.current) { DismissReach.toPx() }
        val hide = stringResource(R.string.in_app_dismiss)
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .graphicsLayer { translationY = drag.value }
                .lens(RoundedCornerShape(28.dp))
                .draggable(
                    state = rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value + delta).coerceAtMost(0f)) } },
                    orientation = Orientation.Vertical,
                    onDragStopped = {
                        if (drag.value < -limit) onDismiss() else drag.animateTo(0f, motion.settle())
                    },
                )
                .clickable(role = Role.Button) { onOpen(current.conversationId) }
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    customActions = listOf(CustomAccessibilityAction(hide) { onDismiss(); true })
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(current.title, size = 40.dp, key = current.conversationId)
            Column(Modifier.weight(1f)) {
                Text(current.title, style = Blesk.type.bodyStrong, maxLines = 1)
                Text(current.line, style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
            }
        }
    }
}

private const val SHOW_MILLIS = 4000L
private val DismissReach = 24.dp
