package app.blesk.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.R
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = Blesk.type.overline,
        color = Blesk.colors.textTertiary,
        modifier = modifier
            .padding(horizontal = Blesk.dimens.gutter + 4.dp)
            .padding(top = Blesk.dimens.sectionGap, bottom = 8.dp)
            .semantics { heading() },
    )
}

/**
 * A settings-style row. [soon] marks it as not ready: it is shown, tagged and inert.
 * Without [onClick] the row is purely informational.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    soon: Boolean = false,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val active = onClick != null && !soon
    val soonLabel = stringResource(R.string.design_soon)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Blesk.dimens.touchTarget + 8.dp)
            .let { if (active) it.clickable(role = Role.Button, onClick = onClick) else it }
            .let { base ->
                if (!soon) return@let base
                base.semantics(mergeDescendants = true) {
                    disabled()
                    contentDescription = "$title, $soonLabel"
                }
            }
            .padding(horizontal = Blesk.dimens.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (soon) Blesk.colors.textTertiary else Blesk.colors.textSecondary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Blesk.type.bodyStrong, color = if (soon) Blesk.colors.textSecondary else Blesk.colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = Blesk.type.label, color = Blesk.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        when {
            soon -> SoonTag()
            trailing != null -> trailing()
            active && showChevron -> Icon(
                BleskIcons.ChevronRight,
                contentDescription = null,
                tint = Blesk.colors.textTertiary,
                size = 20.dp,
            )
        }
    }
}

/**
 * A row with an on/off switch. The whole row toggles. A row that is not [enabled] still shows
 * whether it is on, dimmed, and is announced as unavailable.
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Blesk.dimens.touchTarget + 8.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch) { on ->
                haptics.toggle(on)
                onCheckedChange(on)
            }
            .padding(horizontal = Blesk.dimens.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Blesk.colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(title, style = Blesk.type.bodyStrong, color = if (enabled) Blesk.colors.textPrimary else Blesk.colors.textSecondary)
            if (subtitle != null) {
                Text(subtitle, style = Blesk.type.label, color = Blesk.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(checked, Modifier.alpha(if (enabled) 1f else DIMMED))
    }
}

/**
 * A row with a tick, for one yes-or-no choice inside a step rather than a setting, like
 * subscribing to a channel. The whole row toggles; TalkBack hears a checkbox.
 */
@Composable
fun CheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Blesk.dimens.touchTarget + 8.dp)
            .toggleable(value = checked, role = Role.Checkbox) { on ->
                haptics.toggle(on)
                onCheckedChange(on)
            }
            .padding(horizontal = Blesk.dimens.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Tick(checked)
        Column(Modifier.weight(1f)) {
            Text(title, style = Blesk.type.bodyStrong)
            if (subtitle != null) {
                Text(subtitle, style = Blesk.type.label, color = Blesk.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** The circle of a [CheckRow]: an outline when off, filled with the accent and ticked when on. */
@Composable
private fun Tick(checked: Boolean) {
    val colors = Blesk.colors
    val motion = Blesk.motion
    val fill by animateColorAsState(if (checked) colors.accent else colors.accent.copy(alpha = 0f), motion.select(), label = "tickFill")
    val ring by animateColorAsState(if (checked) colors.accent else colors.textTertiary, motion.select(), label = "tickRing")
    val mark by animateColorAsState(if (checked) colors.onAccent else colors.onAccent.copy(alpha = 0f), motion.select(), label = "tickMark")
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BleskIcons.Check, contentDescription = null, tint = mark, size = 20.dp)
    }
}

/**
 * Visual switch only; the row around it owns the semantics and the click. The knob flows
 * across: its front runs ahead and the rest catches up, stretched for a moment like a drop.
 */
@Composable
fun Switch(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = Blesk.colors
    val motion = Blesk.motion
    val track by animateColorAsState(if (checked) colors.accent else colors.surfaceStrong, motion.select(), label = "track")
    val knob by animateColorAsState(if (checked) colors.onAccent else colors.textSecondary, motion.select(), label = "knob")
    val target = if (checked) 1f else 0f
    val lead = remember { Animatable(target) }
    val tail = remember { Animatable(target) }
    LaunchedEffect(checked) {
        launch { lead.animateTo(target, motion.select()) }
        launch { tail.animateTo(target, motion.stretch()) }
    }
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .size(width = 46.dp, height = 26.dp)
            .clip(CircleShape)
            .background(track)
            .padding(3.dp)
            .metaballs(knob, smoothness = 10.dp) {
                val r = size.height / 2f
                val travel = size.width - size.height
                fun drop(at: Float, scale: Float): Blob {
                    val cx = r + travel * at
                    val rr = r * scale
                    return Blob(Rect(cx - rr, size.height / 2f - rr, cx + rr, size.height / 2f + rr), rr)
                }
                // Two drops in one place would add up to a larger knob, so at rest there is only one.
                val stretch = abs(lead.value - tail.value).coerceAtMost(1f)
                if (stretch < REST) {
                    listOf(drop(lead.value, 1f))
                } else {
                    listOf(drop(lead.value, LEAD_IN_MOTION), drop(tail.value, TAIL_IN_MOTION))
                }
            },
    )
}

private const val REST = 0.02f
private const val DIMMED = 0.4f
private const val LEAD_IN_MOTION = 0.85f
private const val TAIL_IN_MOTION = 0.55f
