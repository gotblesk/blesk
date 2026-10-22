package app.blesk.core.design.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.theme.Blesk

/**
 * A matte sheet at the bottom over a dimmed screen, titled with what it acts on; a tap outside or
 * a swipe down closes it. The sheet is a piece of page: its title, hints and cards lie on the page
 * colour, so they read the same over a busy chat as over an empty screen.
 */
@Composable
fun MenuSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    // Concentric with the cards inside, which sit one inset in from the edge.
    val shape = RoundedCornerShape(Blesk.dimens.cardRadius + SheetInset)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SHEET_SCRIM))
            .dismissOnTap(onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .dragToDismiss(onDismiss)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(Blesk.dimens.gutter)
                .fillMaxWidth()
                .clip(shape)
                .background(Blesk.colors.background)
                .border(Blesk.dimens.hairline, Blesk.colors.hairline, shape)
                .blockTaps()
                .padding(SheetInset),
        ) {
            Text(
                text = title,
                style = Blesk.type.headline,
                maxLines = 1,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .semantics { heading() },
            )
            content()
        }
    }
}

/** How far the screen behind a sheet darkens. */
internal const val SHEET_SCRIM = 0.35f

private val SheetInset = 8.dp
