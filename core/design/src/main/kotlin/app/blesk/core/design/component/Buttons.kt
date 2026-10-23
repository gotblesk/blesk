package app.blesk.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.blesk.core.design.R
import app.blesk.core.design.fire.passingLight
import app.blesk.core.design.theme.Blesk

enum class ButtonKind { Primary, Secondary }

/**
 * A capsule button. While [busy] the action it started is under way: light passes over it, a
 * tap does nothing more, and TalkBack hears «Подожди».
 */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    busy: Boolean = false,
) {
    val colors = Blesk.colors
    val background = if (kind == ButtonKind.Primary) colors.accent else colors.surfaceStrong
    val content = if (kind == ButtonKind.Primary) colors.onAccent else colors.textPrimary
    val wait = stringResource(R.string.design_wait)
    Row(
        modifier = modifier
            .heightIn(min = Blesk.dimens.touchTarget)
            .clip(CircleShape)
            .background(background)
            .passingLight(busy, CircleShape, fill = background, content = content)
            .clickable(role = Role.Button) { if (!busy) onClick() }
            .then(if (busy) Modifier.semantics { stateDescription = wait } else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, size = 20.dp)
        Text(text, style = Blesk.type.bodyStrong, color = content, maxLines = 1)
    }
}

/** A round icon-only button with a 48dp touch target. */
@Composable
fun IconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Blesk.colors.textPrimary,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // A capsule shows behind the icon for a moment, so a bare icon still reads as pressed.
    val capsule by animateFloatAsState(if (pressed) 1f else 0f, Blesk.motion.press(), label = "iconCapsule")
    val fill = Blesk.colors.surfaceStrong
    Box(
        modifier = modifier
            .clip(CircleShape)
            .drawBehind { if (capsule > 0f) drawRect(fill.copy(alpha = fill.alpha * capsule)) }
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}
