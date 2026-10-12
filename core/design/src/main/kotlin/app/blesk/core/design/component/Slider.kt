package app.blesk.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.blesk.core.design.glass.lens
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.collections.immutable.ImmutableList

/**
 * A matte slider. [track] paints the groove, so a hue slider can show the hues it picks from.
 * [valueLabel] is what TalkBack reads for the current value. While dragged, the knob swells and
 * the value rides above it in a small capsule, with a tick at every tenth of the way.
 */
@Composable
fun Slider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    valueLabel: String = "${((value - range.start) / (range.endInclusive - range.start) * 100).toInt()}%",
    track: ImmutableList<Color>? = null,
) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val colors = Blesk.colors
    fun fractionToValue(fraction: Float) = range.start + fraction.coerceIn(0f, 1f) * (range.endInclusive - range.start)
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val haptics = rememberHaptics()
    var dragging by remember { mutableStateOf(false) }
    val swell by animateFloatAsState(if (dragging) 1f else 0f, Blesk.motion.settle(), label = "swell")
    var lastStep by remember { mutableIntStateOf((fraction * STEPS).toInt()) }

    Column(modifier.fillMaxWidth().padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Blesk.type.bodyStrong, modifier = Modifier.weight(1f))
            Text(valueLabel, style = Blesk.type.label, color = Blesk.colors.textSecondary)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Blesk.dimens.touchTarget)
                .semantics {
                    contentDescription = title
                    stateDescription = valueLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(current, range)
                    setProgress { target ->
                        change(target.coerceIn(range.start, range.endInclusive))
                        true
                    }
                }
                .pointerInput(range) {
                    detectTapGestures { change(fractionToValue(it.x / size.width)) }
                }
                .pointerInput(range) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { input, _ ->
                        input.consume()
                        val at = (input.position.x / size.width).coerceIn(0f, 1f)
                        val step = (at * STEPS).toInt()
                        if (step != lastStep) {
                            lastStep = step
                            haptics.tick()
                        }
                        change(fractionToValue(at))
                    }
                }
                .drawBehind {
                    val h = 6.dp.toPx()
                    val y = size.height / 2f - h / 2f
                    val groove = Size(size.width, h)
                    if (track != null) {
                        drawRoundRect(Brush.horizontalGradient(track), Offset(0f, y), groove, CornerRadius(h / 2f))
                    } else {
                        drawRoundRect(colors.surfaceStrong, Offset(0f, y), groove, CornerRadius(h / 2f))
                        drawRoundRect(colors.accent, Offset(0f, y), Size(size.width * fraction, h), CornerRadius(h / 2f))
                    }
                    val knob = 11.dp.toPx() * (1f + SWELL * swell)
                    val x = (size.width * fraction).coerceIn(knob, size.width - knob)
                    drawCircle(colors.background, knob + 2.dp.toPx(), Offset(x, size.height / 2f))
                    drawCircle(colors.textPrimary, knob, Offset(x, size.height / 2f))
                },
        ) {
            if (swell > 0f) {
                Text(
                    text = valueLabel,
                    style = Blesk.type.label,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minWidth = 0))
                            layout(constraints.maxWidth, 0) {
                                val x = (constraints.maxWidth * fraction - placeable.width / 2f)
                                    .coerceIn(0f, (constraints.maxWidth - placeable.width).toFloat())
                                placeable.placeWithLayer(x.toInt(), -placeable.height) { alpha = swell }
                            }
                        }
                        .lens(CircleShape, elevation = 6.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clearAndSetSemantics { },
                )
            }
        }
    }
}

/** A choice of a few options shown as matte pills. */
@Composable
fun Segmented(options: ImmutableList<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Row(
        modifier
            .padding(horizontal = Blesk.dimens.gutter, vertical = 6.dp)
            .clip(CircleShape)
            .background(Blesk.colors.surfaceStrong)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Blesk.dimens.touchTarget)
                    .clip(CircleShape)
                    .background(if (active) Blesk.colors.surface else Color.Transparent)
                    .selectable(selected = active, role = Role.RadioButton) {
                        if (!active) haptics.tick()
                        onSelect(index)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Blesk.type.label, color = if (active) Blesk.colors.textPrimary else Blesk.colors.textSecondary)
            }
        }
    }
}

private const val STEPS = 10
private const val SWELL = 0.35f
