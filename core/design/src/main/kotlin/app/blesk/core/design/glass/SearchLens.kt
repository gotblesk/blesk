package app.blesk.core.design.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlin.math.roundToInt

/**
 * Search unfolding out of the search icon in the top lens: the lens grows from the icon's
 * place at the right edge to the full width, then the field takes the focus. [open] false
 * folds it back into the icon and calls [onClosed] once it is there.
 */
@Composable
fun SearchLens(
    state: TextFieldState,
    hint: String,
    closeLabel: String,
    open: Boolean,
    onClose: () -> Unit,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = Blesk.motion
    val progress = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(open) {
        progress.animateTo(if (open) 1f else 0f, motion.signature())
        if (open) runCatching { focus.requestFocus() } else onClosed()
    }
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .layout { measurable, constraints ->
                    val icon = IconWidth.roundToPx()
                    val width = lerp(icon.toFloat(), constraints.maxWidth.toFloat(), progress.value).roundToInt()
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(width, placeable.height) { placeable.place(0, 0) }
                }
                .height(60.dp)
                .lens(LensShape)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.padding(start = 10.dp)) {
                Icon(BleskIcons.Search, contentDescription = null, tint = Blesk.colors.textSecondary)
            }
            Box(
                Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = progress.value },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.text.isEmpty()) Text(hint, color = Blesk.colors.textTertiary, maxLines = 1)
                BasicTextField(
                    state = state,
                    textStyle = Blesk.type.body.copy(color = Blesk.colors.textPrimary),
                    cursorBrush = SolidColor(Blesk.colors.accent),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .semantics { contentDescription = hint },
                )
            }
            IconButton(BleskIcons.Close, closeLabel, onClose, Modifier.graphicsLayer { alpha = progress.value })
        }
    }
}

private val LensShape = RoundedCornerShape(30.dp)
private val IconWidth = 60.dp
