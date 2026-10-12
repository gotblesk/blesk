package app.blesk.feature.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.Profile
import app.blesk.core.data.settings.VibrationLevel
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.persistentListOf

class SettingsActions(
    val onOpenProfile: () -> Unit,
    val onOpenAppearance: () -> Unit,
    val onOpenPrivacy: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onOpenDebug: () -> Unit,
    val onOpenServer: () -> Unit,
    /** Your bots; only with a real server. */
    val onOpenBots: () -> Unit = {},
    val onOpenNotifications: () -> Unit = {},
    val onOpenStorage: () -> Unit = {},
    val onShowQr: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onOpenSaved: () -> Unit = {},
)

/** The «Ты» tab: you, then everything that is set up once and rarely touched. */
@Composable
fun SettingsList(
    serverHost: String?,
    debugUnlocked: Boolean,
    profile: Profile,
    photo: ImageBitmap?,
    contentPadding: PaddingValues,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    demo: Boolean = serverHost == null,
    scroll: ScrollState = rememberScrollState(),
    sendByEnter: Boolean = false,
    onSendByEnter: (Boolean) -> Unit = {},
    vibration: VibrationLevel = VibrationLevel.Normal,
    onVibration: (VibrationLevel) -> Unit = {},
    header: HeaderStyle = HeaderStyle.Light(0),
    headerPhoto: ImageBitmap? = null,
) {
    val cardPadding = Modifier.padding(horizontal = Blesk.dimens.gutter)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            // The header runs up under the status bar, so only the bottom inset is kept.
            .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        YouHeader(
            profile = profile,
            photo = photo,
            header = header,
            headerPhoto = headerPhoto,
            scroll = { scroll.value },
            actions = YouHeaderActions(onEdit = actions.onOpenProfile, onQr = actions.onShowQr, onShare = actions.onShare),
            demo = demo,
            unnamed = stringResource(R.string.settings_profile),
            labels = YouHeaderLabels(
                edit = stringResource(R.string.you_action_edit),
                qr = stringResource(R.string.you_qr),
                share = stringResource(R.string.you_share),
            ),
        )
        Card(cardPadding) {
            ListRow(title = stringResource(R.string.you_saved), icon = BleskIcons.Bookmark, onClick = actions.onOpenSaved)
            ListRow(title = stringResource(R.string.you_calls), icon = BleskIcons.Phone, soon = true)
            if (serverHost != null) {
                ListRow(
                    title = stringResource(R.string.settings_bots),
                    subtitle = stringResource(R.string.settings_bots_hint),
                    icon = BleskIcons.Bot,
                    onClick = actions.onOpenBots,
                )
            }
            ListRow(
                title = stringResource(R.string.you_security),
                subtitle = stringResource(R.string.settings_privacy_hint),
                icon = BleskIcons.Shield,
                onClick = actions.onOpenPrivacy,
            )
        }
        SectionHeader(stringResource(R.string.settings_section_app))
        Card(cardPadding) {
            ListRow(
                title = stringResource(R.string.settings_appearance),
                subtitle = stringResource(R.string.settings_appearance_hint),
                icon = BleskIcons.Appearance,
                onClick = actions.onOpenAppearance,
            )
            ListRow(
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_hint),
                icon = BleskIcons.Bell,
                onClick = actions.onOpenNotifications,
            )
            ListRow(
                title = stringResource(R.string.settings_storage),
                subtitle = stringResource(R.string.settings_storage_hint),
                icon = BleskIcons.Storage,
                onClick = actions.onOpenStorage,
            )
            ToggleRow(
                title = stringResource(R.string.settings_send_by_enter),
                subtitle = stringResource(R.string.settings_send_by_enter_hint),
                checked = sendByEnter,
                onCheckedChange = onSendByEnter,
                icon = BleskIcons.Send,
            )
            Text(
                text = stringResource(R.string.settings_vibration),
                style = Blesk.type.bodyStrong,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 10.dp),
            )
            Segmented(
                options = persistentListOf(
                    stringResource(R.string.settings_vibration_off),
                    stringResource(R.string.settings_vibration_quiet),
                    stringResource(R.string.settings_vibration_normal),
                ),
                selected = vibration.ordinal,
                onSelect = { onVibration(VibrationLevel.entries[it]) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        SectionHeader(stringResource(R.string.settings_section_connection))
        Card(cardPadding) {
            ListRow(
                title = stringResource(R.string.settings_server),
                subtitle = if (serverHost != null) {
                    stringResource(R.string.settings_server_connected, serverHost)
                } else {
                    stringResource(R.string.settings_server_demo)
                },
                icon = BleskIcons.Server,
                onClick = actions.onOpenServer,
            )
        }
        Card(cardPadding.padding(top = Blesk.dimens.sectionGap)) {
            ListRow(title = stringResource(R.string.settings_about), icon = BleskIcons.Info, onClick = actions.onOpenAbout)
            if (debugUnlocked) {
                ListRow(title = stringResource(R.string.settings_debug), icon = BleskIcons.Debug, onClick = actions.onOpenDebug)
            }
        }
    }
}
