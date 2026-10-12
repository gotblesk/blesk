package app.blesk.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics

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

/** A row with an on/off switch. The whole row toggles. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Blesk.dimens.touchTarget + 8.dp)
            .toggleable(value = checked, role = Role.Switch) { on ->
                haptics.toggle(on)
                onCheckedChange(on)
            }
            .padding(horizontal = Blesk.dimens.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Blesk.colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(title, style = Blesk.type.bodyStrong)
            if (subtitle != null) {
                Text(subtitle, style = Blesk.type.label, color = Blesk.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(checked)
    }
}

/** Visual switch only; the row around it owns the semantics and the click. */
@Composable
fun Switch(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = Blesk.colors
    val track by animateColorAsState(if (checked) colors.accent else colors.surfaceStrong, Blesk.motion.select(), label = "track")
    val knob by animateColorAsState(if (checked) colors.onAccent else colors.textSecondary, Blesk.motion.select(), label = "knob")
    val shift by animateDpAsState(if (checked) 20.dp else 0.dp, Blesk.motion.select(), label = "shift")
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .size(width = 46.dp, height = 26.dp)
            .clip(CircleShape)
            .background(track)
            .padding(3.dp),
    ) {
        Spacer(
            Modifier
                .offset(x = shift)
                .size(20.dp)
                .background(knob, CircleShape),
        )
    }
}
