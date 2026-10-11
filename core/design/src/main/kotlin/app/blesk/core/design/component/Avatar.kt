package app.blesk.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import app.blesk.core.design.theme.Blesk

/**
 * A monogram avatar: the first letter of the name in Onest on a matte circle. People set a
 * photo later; until then there are no generated faces or placeholders.
 */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = Blesk.dimens.avatar) {
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString().orEmpty()
    Box(
        modifier = modifier
            .size(size)
            .background(Blesk.colors.surfaceStrong, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = Blesk.type.headline.copy(fontSize = Blesk.type.headline.fontSize * (size.value / 48f)),
            color = Blesk.colors.textSecondary,
            maxLines = 1,
        )
    }
}
