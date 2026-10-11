package app.blesk.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.blesk.core.data.settings.NotificationPreview
import app.blesk.core.data.settings.NotificationSettings
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.persistentListOf

@Composable
fun NotificationsScreen(
    settings: NotificationSettings,
    /** False in the demo: there is no connection to keep. */
    serverConnected: Boolean,
    onChange: (NotificationSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    ScrollScreen(title = stringResource(R.string.notifications_title), onBack = onBack, modifier = modifier) {
        Card(pad) {
            ToggleRow(
                title = stringResource(R.string.notifications_enabled),
                checked = settings.enabled,
                onCheckedChange = { onChange(settings.copy(enabled = it)) },
            )
        }
        if (settings.enabled) {
            SectionHeader(stringResource(R.string.notifications_preview))
            Segmented(
                options = persistentListOf(
                    stringResource(R.string.notifications_preview_full),
                    stringResource(R.string.notifications_preview_name),
                    stringResource(R.string.notifications_preview_nothing),
                ),
                selected = settings.preview.ordinal,
                onSelect = { onChange(settings.copy(preview = NotificationPreview.entries[it])) },
            )
            Text(
                text = stringResource(R.string.notifications_preview_hint),
                style = Blesk.type.label,
                color = Blesk.colors.textSecondary,
                modifier = pad.padding(top = Blesk.dimens.rowGap),
            )
        }
        Card(pad.padding(top = Blesk.dimens.sectionGap)) {
            if (serverConnected) {
                ToggleRow(
                    title = stringResource(R.string.notifications_online),
                    subtitle = stringResource(R.string.notifications_online_hint),
                    checked = settings.alwaysOnline,
                    onCheckedChange = { onChange(settings.copy(alwaysOnline = it)) },
                )
            } else {
                ListRow(
                    title = stringResource(R.string.notifications_online),
                    subtitle = stringResource(R.string.notifications_online_demo),
                    showChevron = false,
                )
            }
        }
    }
}
