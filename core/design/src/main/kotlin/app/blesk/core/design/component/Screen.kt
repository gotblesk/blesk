package app.blesk.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens

/**
 * A scrolling page under the top lens. [bottom] is the bottom lens, if the page has one. The page
 * ends at the keyboard, so the field being typed in stays in sight above it.
 */
@Composable
fun ScrollScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    LensScaffold(
        modifier = modifier,
        top = { TopLens(title = title, onBack = onBack, actions = actions) },
        bottom = bottom,
    ) { padding ->
        val keyboard = WindowInsets.ime
        val density = LocalDensity.current
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(keyboard.only(WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(remember(padding, keyboard, density) { AboveKeyboard(padding, keyboard, density) }),
        ) {
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * The lenses' room less the part of the bottom one the keyboard covers: the page already ends at
 * the keyboard, so a bar left under it takes no room and a lens riding on it only its own height.
 * Read while measuring, so the keyboard moving relayouts the page without recomposing it.
 */
@Stable
private class AboveKeyboard(private val lenses: PaddingValues, private val keyboard: WindowInsets, private val density: Density) :
    PaddingValues {
    override fun calculateTopPadding(): Dp = lenses.calculateTopPadding()

    override fun calculateBottomPadding(): Dp =
        (lenses.calculateBottomPadding() - with(density) { keyboard.getBottom(this).toDp() }).coerceAtLeast(0.dp)

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = lenses.calculateLeftPadding(layoutDirection)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = lenses.calculateRightPadding(layoutDirection)
}
