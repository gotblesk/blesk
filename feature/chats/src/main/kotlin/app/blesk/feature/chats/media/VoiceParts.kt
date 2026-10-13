package app.blesk.feature.chats.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * A voice note, laid straight into its message bubble: the button, then a wave of thin rounded
 * bars cut from the real loudness, with the time and the speed under it. What has been heard
 * shines in the theme's light, the rest stays matte, and one glint marks where the voice is.
 * A tap or a slide on the wave seeks. No capsule of its own and no outline: the bubble is the
 * capsule, and it breathes with the voice while it plays (see MessageColumn).
 */
@Composable
internal fun VoiceWave(
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
    val glint = Blesk.colors.light
    // White light vanishes on a pale bubble: there the heard bars are ink and only the glint shines.
    val heard = if (Blesk.colors.isDark) glint else Blesk.colors.textPrimary
    val ahead = Blesk.colors.textPrimary.copy(alpha = MATTE)
    var dragging by remember { mutableFloatStateOf(-1f) }
    var lastTenth by remember { mutableIntStateOf(-1) }
    val shown = if (dragging >= 0f) dragging else progress
    val shape = remember(bars) { resample(bars, BAR_COUNT) }
    val waveLabel = stringResource(R.string.player_wave)
    Row(modifier.width(VoiceWidth), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                .padding(11.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(WaveHeight)
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
                drawBars(shape, shown, heard, ahead)
                if (playing || shown > 0f) drawGlint(shown, glint)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(time, style = Blesk.type.caption, color = Blesk.colors.textSecondary)
                if (speed != null) SpeedCapsule(speed, onCycleSpeed, onPickSpeed)
            }
        }
    }
}

/** [bars] stretched or squeezed to [count] values, taking the loudest of each stretch. */
private fun resample(bars: FloatArray, count: Int): FloatArray {
    if (bars.isEmpty()) return FloatArray(count)
    return FloatArray(count) { i ->
        val from = i * bars.size / count
        val to = ((i + 1) * bars.size / count).coerceAtLeast(from + 1).coerceAtMost(bars.size)
        (from until to).maxOf { bars[it] }
    }
}

private fun DrawScope.drawBars(bars: FloatArray, heard: Float, lit: Color, ahead: Color) {
    val width = BarWidth.toPx()
    val step = size.width / bars.size
    val lowest = BarMin.toPx()
    val middle = size.height / 2f
    bars.forEachIndexed { i, value ->
        val x = step * i + step / 2f
        val half = (lowest + (size.height - lowest) * value.coerceIn(0f, 1f)) / 2f
        val color = if (x <= size.width * heard) lit else ahead
        drawLine(color, Offset(x, middle - half), Offset(x, middle + half), width, StrokeCap.Round)
    }
}

/** The one glint where the voice has got to. */
private fun DrawScope.drawGlint(at: Float, light: Color) {
    val center = Offset(size.width * at, size.height / 2f)
    val radius = GlintRadius.toPx()
    drawCircle(Brush.radialGradient(listOf(light, light.copy(alpha = 0f)), center = center, radius = radius), radius, center)
}

private val VoiceWidth = 248.dp
private val WaveHeight = 30.dp
private val ButtonSize = 40.dp
private val BarWidth = 2.dp
private val BarMin = 3.dp
private val GlintRadius = 9.dp
private const val BAR_COUNT = 48
private const val MATTE = 0.28f
private const val BADGE = 0.12f
private const val TENTHS = 10
