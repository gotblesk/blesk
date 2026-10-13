package app.blesk.economy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.R
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Text
import app.blesk.core.design.glass.lens
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The economy mode speaking from the top lens: once per discharge «Заряд 18 %. Включить
 * экономный режим?» with «Включить» and «Не сейчас», and once a short «Экономный режим включён»
 * with «Настроить» when «Авто» has turned it on by itself. A swipe up puts either away.
 */
@Composable
internal fun EconomyNotices(monitor: EconomyMonitor, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val offer by monitor.offer.collectAsStateWithLifecycle()
    val announce by monitor.announce.collectAsStateWithLifecycle()
    Column(modifier) {
        TopCapsule(
            level = offer,
            text = { stringResource(R.string.economy_offer, it) },
            onDismiss = monitor::dismissOffer,
        ) {
            Button(stringResource(R.string.economy_offer_on), onClick = monitor::acceptOffer)
            Button(stringResource(R.string.economy_offer_later), onClick = monitor::dismissOffer, kind = ButtonKind.Secondary)
        }
        LaunchedEffect(announce) {
            if (announce == null) return@LaunchedEffect
            delay(ANNOUNCE_MILLIS)
            monitor.dismissAnnounce()
        }
        TopCapsule(
            level = announce,
            text = { stringResource(R.string.economy_announce, it) },
            onDismiss = monitor::dismissAnnounce,
        ) {
            Button(
                text = stringResource(R.string.economy_announce_settings),
                onClick = {
                    monitor.dismissAnnounce()
                    onSettings()
                },
                kind = ButtonKind.Secondary,
            )
        }
    }
}

@Composable
private fun TopCapsule(
    level: Int?,
    text: @Composable (Int) -> String,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
) {
    var shown by remember { mutableIntStateOf(0) }
    if (level != null) shown = level
    val motion = Blesk.motion
    val haptics = rememberHaptics()
    LaunchedEffect(level) { if (level != null) haptics.tick() }
    AnimatedVisibility(
        visible = level != null,
        enter = slideInVertically(motion.settle()) { -it } + scaleIn(motion.settle(), initialScale = 0.6f) + fadeIn(motion.press()),
        exit = slideOutVertically(motion.transition()) { -it } + fadeOut(motion.transition()),
    ) {
        val drag = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val limit = with(LocalDensity.current) { DismissReach.toPx() }
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .graphicsLayer { translationY = drag.value }
                .lens(RoundedCornerShape(28.dp))
                .draggable(
                    state = rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value + delta).coerceAtMost(0f)) } },
                    orientation = Orientation.Vertical,
                    onDragStopped = { if (drag.value < -limit) onDismiss() else drag.animateTo(0f, motion.settle()) },
                )
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text(shown), style = Blesk.type.bodyStrong)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
        }
    }
}

private const val ANNOUNCE_MILLIS = 5000L
private val DismissReach = 24.dp
