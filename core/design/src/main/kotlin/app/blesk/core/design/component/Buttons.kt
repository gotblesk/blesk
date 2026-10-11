package app.blesk.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.blesk.core.design.theme.Blesk

enum class ButtonKind { Primary, Secondary }

@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
) {
    val colors = Blesk.colors
    val background = if (kind == ButtonKind.Primary) colors.accent else colors.surfaceStrong
    val content = if (kind == ButtonKind.Primary) colors.onAccent else colors.textPrimary
    Row(
        modifier = modifier
            .heightIn(min = Blesk.dimens.touchTarget)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
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
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = null, onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
