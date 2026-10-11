package app.blesk.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk

@Composable
fun SettingsScreen(
    demo: Boolean,
    debugUnlocked: Boolean,
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardPadding = Modifier.padding(horizontal = Blesk.dimens.gutter)
    ScrollScreen(title = stringResource(R.string.settings_title), onBack = onBack, modifier = modifier) {
        SectionHeader(stringResource(R.string.settings_section_app))
        Card(cardPadding) {
            ListRow(
                title = stringResource(R.string.settings_appearance),
                subtitle = stringResource(R.string.settings_appearance_hint),
                icon = BleskIcons.Appearance,
                onClick = onOpenAppearance,
            )
            ListRow(title = stringResource(R.string.settings_notifications), icon = BleskIcons.Chats, soon = true)
            ListRow(title = stringResource(R.string.settings_privacy), icon = BleskIcons.Lock, soon = true)
            ListRow(title = stringResource(R.string.settings_storage), icon = BleskIcons.Copy, soon = true)
        }
        SectionHeader(stringResource(R.string.settings_section_connection))
        Card(cardPadding) {
            ListRow(
                title = stringResource(R.string.settings_server),
                subtitle = if (demo) stringResource(R.string.settings_server_demo) else null,
                icon = BleskIcons.Server,
                soon = true,
            )
        }
        Card(cardPadding.padding(top = Blesk.dimens.sectionGap)) {
            ListRow(title = stringResource(R.string.settings_about), icon = BleskIcons.Info, onClick = onOpenAbout)
            if (debugUnlocked) {
                ListRow(title = stringResource(R.string.settings_debug), icon = BleskIcons.Debug, onClick = onOpenDebug)
            }
        }
    }
}
