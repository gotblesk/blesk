package app.blesk.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens

/** A scrolling page under the top lens. [bottom] is the bottom lens, if the page has one. */
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
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}
