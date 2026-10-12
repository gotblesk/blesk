package app.blesk.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.design.theme.Blesk

/**
 * A screen or a section that has nothing to show yet, and why: an error, no access, nothing
 * here. One short line, and at most one way out of it.
 */
@Composable
fun StateMessage(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 16.dp)
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, color = Blesk.colors.textSecondary, textAlign = TextAlign.Center)
        if (action != null && onAction != null) Button(text = action, kind = ButtonKind.Secondary, onClick = onAction)
    }
}
