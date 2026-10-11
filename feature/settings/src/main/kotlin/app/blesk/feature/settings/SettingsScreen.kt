package app.blesk.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk

/** The settings tab, placed under the home lenses. */
@Composable
fun SettingsList(
    demo: Boolean,
    debugUnlocked: Boolean,
    contentPadding: PaddingValues,
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardPadding = Modifier.padding(horizontal = Blesk.dimens.gutter)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        SectionHeader(stringResource(R.string.settings_section_app), Modifier.padding(top = 0.dp))
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
