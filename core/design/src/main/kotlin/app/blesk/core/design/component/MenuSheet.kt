package app.blesk.core.design.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.R
import app.blesk.core.design.theme.Blesk

/**
 * A matte sheet at the bottom over a dimmed screen, titled with what it acts on; a tap outside or
 * a swipe down closes it. The sheet is a piece of page: its title, hints and cards lie on the page
 * colour, so they read the same over a busy chat as over an empty screen. It keeps clear of the
 * bars and the keyboard; a tall one shrinks, and a part given `Modifier.weight(1f, fill = false)`
 * takes what is left after the title and the buttons.
 */
@Composable
fun MenuSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Concentric with the cards inside, which sit one inset in from the edge.
    val shape = RoundedCornerShape(Blesk.dimens.cardRadius + SheetInset)
    SheetOverDimmedScreen(onDismiss) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Blesk.dimens.gutter)
                .dragToDismiss(onDismiss)
                .fillMaxWidth()
                .clip(shape)
                .background(Blesk.colors.background)
                .border(Blesk.dimens.hairline, Blesk.colors.hairline, shape)
                .catchesTaps()
                .semantics { paneTitle = title }
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

/**
 * The dimmed screen with [sheet] over it. The dimmed area lies beside the sheet, not under it: a tap on the sheet stays
 * there, and the sheet's own title and rows are not merged into one button that closes it.
 */
@Composable
internal fun SheetOverDimmedScreen(onDismiss: () -> Unit, sheet: @Composable BoxScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    val close = stringResource(R.string.design_close)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = SHEET_SCRIM))
                .dismissOnTap(onDismiss, close)
                .semantics { contentDescription = close },
        )
        sheet()
    }
}

/** The page under an open sheet: out of TalkBack's reach, as the dimmed area keeps it out of a finger's. */
fun Modifier.underSheet(covered: Boolean): Modifier = if (covered) clearAndSetSemantics {} else this

/** How far the screen behind a sheet darkens. */
internal const val SHEET_SCRIM = 0.35f

private val SheetInset = 8.dp
