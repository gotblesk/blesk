package app.blesk.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.R
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.settings.ThemePicker
import app.blesk.shared.appearance.ThemeSpec

/** Theme of a single chat: pick one of the themes, fall back to the app theme, or share it. */
@Composable
internal fun ChatThemeSheet(
    current: ThemeSpec,
    overridden: Boolean,
    onSelect: (ThemeSpec?) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(Blesk.dimens.gutter)
                .clip(RoundedCornerShape(Blesk.dimens.cardRadius))
                .background(Blesk.colors.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_theme_title),
                style = Blesk.type.headline,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = 14.dp),
            )
            ThemePicker(current = current, onSelect = { onSelect(it) })
            if (overridden) {
                ListRow(
                    title = stringResource(R.string.chat_theme_app),
                    icon = BleskIcons.Close,
                    showChevron = false,
                    onClick = { onSelect(null) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            ListRow(
                title = stringResource(R.string.chat_theme_send),
                icon = BleskIcons.Send,
                showChevron = false,
                onClick = onSend,
            )
        }
    }
}
