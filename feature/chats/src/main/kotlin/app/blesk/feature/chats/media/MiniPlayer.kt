package app.blesk.feature.chats.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.glass.lens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.Playback
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What the small player can be asked to do. */
class MiniPlayerActions(
    val onToggle: () -> Unit,
    val onStop: () -> Unit,
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    /** Opens the whole player; null where there is no sheet to open. */
    val onExpand: (() -> Unit)? = null,
)

/**
 * What is playing, on a small capsule of lens glass stuck to the bottom lens: the name, a line of
 * the theme's light for how far along it is, play or pause, and a cross to stop. Swiped sideways
 * it goes to the next or the previous song, down it stops, up or with a tap it opens the player.
 */
@Composable
fun MiniPlayer(playback: Playback, actions: MiniPlayerActions, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = playback.path != null,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(Blesk.dimens.capsuleRadius)
        val haptics = rememberHaptics()
        val scope = rememberCoroutineScope()
        val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val threshold = with(LocalDensity.current) { SwipeDistance.toPx() }
        val labels = MiniLabels(
            next = stringResource(R.string.player_next),
            previous = stringResource(R.string.player_previous),
            stop = stringResource(R.string.player_stop),
            expand = stringResource(R.string.player_expand),
        )
        Column(
            Modifier
                .padding(horizontal = Blesk.dimens.gutter)
                .padding(bottom = 8.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = drag.value.x
                    translationY = drag.value.y.coerceAtLeast(0f)
                    alpha = 1f - (abs(drag.value.x) / (threshold * 3f)).coerceIn(0f, 0.5f)
                }
                .lens(shape)
                .clip(shape)
                .let { capsule ->
                    val expand = actions.onExpand ?: return@let capsule
                    capsule.clickable(role = Role.Button, onClickLabel = labels.expand, onClick = expand)
                }
                .pointerInput(actions) {
                    detectDragGestures(
                        onDragEnd = {
                            val (x, y) = drag.value
                            when {
                                x > threshold -> actions.onPrevious().also { haptics.tick() }
                                x < -threshold -> actions.onNext().also { haptics.tick() }
                                y > threshold -> actions.onStop().also { haptics.detach() }
                                y < -threshold -> actions.onExpand?.invoke()
                            }
                            scope.launch { drag.animateTo(Offset.Zero) }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(Offset.Zero) } },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { drag.snapTo(drag.value + amount) }
                    }
                }
                .semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(labels.next) { actions.onNext(); true },
                        CustomAccessibilityAction(labels.previous) { actions.onPrevious(); true },
                    )
                },
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(playback.title.ifEmpty { stringResource(R.string.player_untitled) }, style = Blesk.type.bodyStrong, maxLines = 1)
                    if (playback.artist.isNotEmpty()) {
                        Text(playback.artist, style = Blesk.type.caption, color = Blesk.colors.textSecondary, maxLines = 1)
                    }
                }
                IconButton(
                    icon = if (playback.playing) BleskIcons.Pause else BleskIcons.Play,
                    contentDescription = stringResource(if (playback.playing) R.string.media_pause else R.string.media_play),
                    onClick = actions.onToggle,
                )
                IconButton(BleskIcons.Close, labels.stop, actions.onStop)
            }
            val fraction = if (playback.durationMillis > 0) {
                (playback.positionMillis.toFloat() / playback.durationMillis).coerceIn(0f, 1f)
            } else {
                0f
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Blesk.colors.hairline)) {
                Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(Blesk.colors.light, CircleShape))
            }
        }
    }
}

private class MiniLabels(val next: String, val previous: String, val stop: String, val expand: String)

private val SwipeDistance = 56.dp
