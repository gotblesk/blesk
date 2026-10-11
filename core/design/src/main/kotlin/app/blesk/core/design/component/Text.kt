package app.blesk.core.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blesk.core.design.theme.Blesk

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Blesk.type.body,
    color: Color = Blesk.colors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(color = color, textAlign = textAlign),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** An icon from [app.blesk.core.design.icon.BleskIcons]. Decorative when [contentDescription] is null. */
@Composable
fun Icon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Blesk.colors.textPrimary,
    size: Dp = 24.dp,
) {
    Image(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
