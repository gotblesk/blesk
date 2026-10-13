package app.blesk.feature.chats.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.AudioPlayer
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/** «1×», «1,5×», «0,75×»: the way the speed is written on its capsule. */
internal fun speedLabel(speed: Float): String {
    val text = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else "%s".format(Locale.ROOT, speed).trimEnd('0')
    return text.replace('.', ',') + "×"
}

/**
 * The speed of voice notes and round videos on a small capsule. A tap goes 1× → 1,5× → 2× → 1×
 * with a tick and a spark; a long press opens every speed from 0,5× to 2×.
 */
@Composable
internal fun SpeedCapsule(speed: Float, onCycle: () -> Unit, onPick: (Float) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    var picking by remember { mutableStateOf(false) }
    val label = speedLabel(speed)
    val all = stringResource(R.string.media_speed_all)
    Box(modifier) {
        Text(
            text = label,
            style = Blesk.type.caption,
            color = Blesk.colors.textPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .flashOn(speed, CircleShape)
                .background(Blesk.colors.textPrimary.copy(alpha = BADGE))
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.media_speed, label),
                    onLongClickLabel = all,
                    onLongClick = {
                        haptics.menu()
                        picking = true
                    },
                ) {
                    haptics.tick()
                    onCycle()
                }
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        if (picking) {
            Popup(
                alignment = Alignment.BottomStart,
                onDismissRequest = { picking = false },
                properties = PopupProperties(focusable = true),
            ) {
                Row(
                    Modifier
                        .padding(bottom = 28.dp)
                        .clip(CircleShape)
                        .background(Blesk.colors.surfaceStrong)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    AudioPlayer.SPEEDS.forEach { option ->
                        val chosen = option == speed
                        Text(
                            text = speedLabel(option),
                            style = Blesk.type.caption,
                            color = if (chosen) Blesk.colors.onAccent else Blesk.colors.textPrimary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (chosen) Blesk.colors.accent else Color.Transparent)
                                .clickable(role = Role.Button) {
                                    haptics.tick()
                                    onPick(option)
                                    picking = false
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A voice note as a capsule whose own edge is the voice: the top and the bottom follow the
 * loudness, mirrored, by at most 6 dp, so the capsule stays neat. The played part of the edge
 * glows and a glint runs along it; a tap or a slide anywhere on the wave seeks. The button,
 * the time and the speed sit inside.
 */
@Composable
internal fun WaveCapsule(
    bars: FloatArray,
    progress: Float,
    playing: Boolean,
    time: String,
    speed: Float?,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    onPickSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val fill = Blesk.colors.textPrimary.copy(alpha = FILL)
    val ahead = Blesk.colors.textPrimary.copy(alpha = EDGE)
    val glint = Blesk.colors.light
    // White light vanishes on a pale bubble: there the played edge is drawn in ink and only the glint shines.
    val lit = if (Blesk.colors.isDark) glint else Blesk.colors.textPrimary
    var dragging by remember { mutableFloatStateOf(-1f) }
    var lastTenth by remember { mutableIntStateOf(-1) }
    val shown = if (dragging >= 0f) dragging else progress
    val waveLabel = stringResource(R.string.player_wave)
    Box(modifier.width(CapsuleWidth).height(CapsuleHeight)) {
        Canvas(
            Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = waveLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                    setProgress { value -> onSeek(value); true }
                }
                .pointerInput(onSeek) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) } }
                .pointerInput(onSeek) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragging >= 0f) onSeek(dragging)
                            dragging = -1f
                        },
                    ) { change, _ ->
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        val tenth = (f * TENTHS).toInt()
                        if (tenth != lastTenth) {
                            lastTenth = tenth
                            haptics.tick()
                        }
                        dragging = f
                    }
                },
        ) {
            val outline = waveOutline(bars, Swing.toPx())
            drawPath(outline, fill)
            val stroke = Stroke(EdgeWidth.toPx(), cap = StrokeCap.Round)
            drawPath(outline, ahead, style = stroke)
            clipRect(right = size.width * shown) { drawPath(outline, lit, style = stroke) }
            if (playing || shown > 0f) drawGlint(shown, glint)
        }
        Row(
            Modifier.matchParentSize().padding(start = 6.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val action = stringResource(if (playing) R.string.media_pause else R.string.media_play)
            Icon(
                icon = if (playing) BleskIcons.Pause else BleskIcons.Play,
                contentDescription = action,
                tint = Blesk.colors.onAccent,
                size = 18.dp,
                modifier = Modifier
                    .size(ButtonSize)
                    .clip(CircleShape)
                    .background(Blesk.colors.accent)
                    .clickable(role = Role.Button, onClickLabel = action, onClick = onToggle)
                    .padding(9.dp),
            )
            Box(Modifier.weight(1f))
            Text(time, style = Blesk.type.caption, color = Blesk.colors.textSecondary)
            if (speed != null) SpeedCapsule(speed, onCycleSpeed, onPickSpeed)
        }
    }
}

/**
 * The capsule outline: straight ends of a capsule, and between them a top and a bottom edge that
 * swell outwards with the voice. The swell fades towards the ends so they stay round.
 */
private fun DrawScope.waveOutline(bars: FloatArray, swing: Float): Path {
    val h = size.height
    val r = h / 2f - swing
    val left = r + swing
    val right = size.width - r - swing
    val steps = bars.size.coerceAtLeast(2)
    fun amp(i: Int): Float {
        val x = i.toFloat() / (steps - 1)
        val taper = sin(PI.toFloat() * x).coerceIn(0f, 1f)
        return bars.getOrElse(i * bars.size / steps) { 0f } * swing * taper
    }
    return Path().apply {
        moveTo(left, swing)
        for (i in 0 until steps) {
            val x = left + (right - left) * i / (steps - 1)
            lineTo(x, swing - amp(i))
        }
        arcTo(Rect(right - r, swing, right + r, h - swing), -90f, 180f, false)
        for (i in steps - 1 downTo 0) {
            val x = left + (right - left) * i / (steps - 1)
            lineTo(x, h - swing + amp(i))
        }
        arcTo(Rect(left - r, swing, left + r, h - swing), 90f, 180f, false)
        close()
    }
}

/** A short bright glint on both edges where the voice has got to. */
private fun DrawScope.drawGlint(at: Float, light: Color) {
    val x = size.width * at
    val radius = GlintRadius.toPx()
    listOf(Swing.toPx(), size.height - Swing.toPx()).forEach { y ->
        val glow = Brush.radialGradient(listOf(light, light.copy(alpha = 0f)), center = Offset(x, y), radius = radius)
        drawCircle(glow, radius, Offset(x, y))
    }
}

private val CapsuleWidth = 240.dp
private val CapsuleHeight = 52.dp
private val ButtonSize = 36.dp
private val Swing = 6.dp
private val EdgeWidth = 1.5.dp
private val GlintRadius = 7.dp
private const val FILL = 0.07f
private const val EDGE = 0.22f
private const val BADGE = 0.12f
private const val TENTHS = 10
