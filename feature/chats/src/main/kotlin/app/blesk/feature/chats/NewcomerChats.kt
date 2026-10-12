package app.blesk.feature.chats

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Text
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.motion.rememberLoopPhase
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Who you are on the empty screen, and the first friend once one arrives. */
class Newcomer(val name: String, val key: String, val photo: ImageBitmap?)

/**
 * «Чаты» before anyone is there: your own capsule with light slowly crossing it, and the two
 * ways to change that. When [friend] appears, their capsule flies in and sticks to yours, the
 * pair flashes, and [onSettled] hands the screen over to the ordinary list.
 */
@Composable
fun NewcomerChats(
    me: Newcomer,
    contentPadding: PaddingValues,
    onFind: () -> Unit,
    modifier: Modifier = Modifier,
    onInvite: (() -> Unit)? = null,
    friend: Newcomer? = null,
    onSettled: () -> Unit = {},
) {
    val motion = Blesk.motion
    val haptics = rememberHaptics()
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(friend) {
        if (friend == null) return@LaunchedEffect
        if (motion.reduced) arrival.snapTo(1f) else arrival.animateTo(1f, motion.signature())
        haptics.stick()
        delay(SETTLE_MILLIS)
        onSettled()
    }
    val density = LocalDensity.current
    val capsule = Blesk.colors.surface
    val stuck = friend != null && arrival.value >= 1f
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = Blesk.dimens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                // Room for the friend grows as they arrive, so the rest of the screen makes way instead of waiting under a gap.
                .size(CapsuleWidth, CapsuleHeight + (CapsuleHeight + CapsuleGap) * if (friend == null) 0f else arrival.value)
                .metaballs(capsule, smoothness = 24.dp) {
                    val h = CapsuleHeight.toPx()
                    val radius = h / 2f
                    val mine = Rect(0f, 0f, size.width, h)
                    // The friend joins the drop only once close; in flight their capsule carries its own fill.
                    if (friend == null || arrival.value < JOIN_AT) return@metaballs listOf(Blob(mine, radius))
                    val top = h + CapsuleGap.toPx() + (1f - arrival.value) * FlyFrom.toPx()
                    listOf(Blob(mine, radius), Blob(Rect(0f, top, size.width, top + h), radius))
                }
                .flashOn(stuck, RoundedCornerShape(CapsuleHeight / 2)),
        ) {
            CapsuleContent(me, Modifier.lightPassing())
            if (friend != null) {
                CapsuleContent(
                    who = friend,
                    modifier = Modifier
                        .offset {
                            val fly = with(density) { FlyFrom.toPx() } * (1f - arrival.value)
                            IntOffset(0, (with(density) { (CapsuleHeight + CapsuleGap).toPx() } + fly).roundToInt())
                        }
                        .graphicsLayer { alpha = arrival.value }
                        .background(if (arrival.value < JOIN_AT) capsule else Color.Transparent, RoundedCornerShape(CapsuleHeight / 2))
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Text(
            text = stringResource(R.string.newcomer_text),
            color = Blesk.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp, start = 24.dp, end = 24.dp),
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onInvite != null) {
                Button(
                    text = stringResource(R.string.newcomer_invite),
                    onClick = onInvite,
                    icon = BleskIcons.Invite,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                text = stringResource(R.string.newcomer_find),
                onClick = onFind,
                icon = BleskIcons.At,
                kind = if (onInvite != null) ButtonKind.Secondary else ButtonKind.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CapsuleContent(who: Newcomer, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .size(CapsuleWidth, CapsuleHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(who.name, size = CapsuleHeight - 16.dp, photo = who.photo, key = who.key)
        Text(who.name, style = Blesk.type.bodyStrong, maxLines = 1)
    }
}

/** A soft band of the theme's light crossing the capsule, slowly, again and again. Still when motion is reduced. */
@Composable
private fun Modifier.lightPassing(): Modifier {
    val phase by rememberLoopPhase(LIGHT_MILLIS)
    val light = Blesk.colors.light.copy(alpha = if (Blesk.colors.isDark) LIGHT_DARK else LIGHT_LIGHT)
    if (Blesk.motion.reduced) return this
    return drawWithContent {
        val band = size.width * BAND
        val x = -band + (size.width + band * 2f) * phase
        drawRect(
            Brush.horizontalGradient(listOf(light.copy(alpha = 0f), light, light.copy(alpha = 0f)), startX = x - band, endX = x + band),
            topLeft = Offset.Zero,
            size = size,
            alpha = 1f,
        )
        drawContent()
    }
}

private val CapsuleWidth = 240.dp
private val CapsuleHeight = 64.dp
private val CapsuleGap = 4.dp
private val FlyFrom = 160.dp
private const val SETTLE_MILLIS = 900L
private const val JOIN_AT = 0.85f
private const val LIGHT_MILLIS = 4200
private const val BAND = 0.35f
private const val LIGHT_LIGHT = 0.5f
private const val LIGHT_DARK = 0.18f
