package app.blesk.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.glint
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.BleskPalette
import app.blesk.shared.appearance.ThemeSpec

/** A theme shared in a chat: a miniature of it and a button to apply it. */
@Composable
internal fun ThemeCard(spec: ThemeSpec, onApply: (ThemeSpec) -> Unit) {
    val colors = remember(spec) { BleskPalette.from(spec) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 64.dp, height = 44.dp)
                .clip(shape)
                .background(colors.background)
                .glint(shape, color = colors.light, onDark = colors.isDark),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(10.dp).background(colors.accent, CircleShape))
        }
        Text(stringResource(R.string.theme_card_title), style = Blesk.type.bodyStrong)
        Button(
            text = stringResource(R.string.theme_card_apply),
            kind = ButtonKind.Secondary,
            onClick = { onApply(spec) },
        )
    }
}
