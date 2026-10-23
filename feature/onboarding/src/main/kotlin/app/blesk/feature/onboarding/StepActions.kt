package app.blesk.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk

/** One of the buttons at the foot of a step. */
internal class StepButton(val text: String, val onClick: () -> Unit, val busy: Boolean = false)

/**
 * The foot of a step: its main action, maybe a second one under it, and a [note] above them.
 * It stays at the bottom, clear of the fields, where a thumb finds it; the page scrolls under a
 * veil of the background. Under the keyboard it is hidden, and the keyboard's own action key
 * does the same as [primary].
 */
@Composable
internal fun StepActions(
    primary: StepButton,
    modifier: Modifier = Modifier,
    secondary: StepButton? = null,
    note: (@Composable () -> Unit)? = null,
) {
    val background = Blesk.colors.background
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val veil = (VeilHeight.toPx() / size.height).coerceIn(0f, 1f)
                drawRect(Brush.verticalGradient(0f to Color.Transparent, veil to background, 1f to background))
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = Blesk.dimens.gutter, end = Blesk.dimens.gutter, top = VeilHeight, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        note?.invoke()
        Button(primary.text, onClick = primary.onClick, modifier = Modifier.fillMaxWidth(), busy = primary.busy)
        if (secondary != null) {
            Button(
                secondary.text,
                onClick = secondary.onClick,
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Secondary,
                busy = secondary.busy,
            )
        }
    }
}

/** A step's explanation under its title. */
@Composable
internal fun StepText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Blesk.colors.textSecondary,
        modifier = modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).padding(bottom = 12.dp),
    )
}

/** The page fades into the foot of a step over this height instead of ending at a hard edge. */
private val VeilHeight = 24.dp
