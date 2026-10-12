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
import app.blesk.core.data.model.GLINT_REACTION
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
import app.blesk.core.design.motion.screenKey
import app.blesk.core.design.motion.sharedBounds
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

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

/** The «Ты» tab: your profile and what is yours — saved messages, calls, invites, stickers, bots. */
@Composable
fun YouList(
    serverHost: String?,
    profile: Profile,
    photo: ImageBitmap?,
    contentPadding: PaddingValues,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    demo: Boolean = serverHost == null,
    scroll: ScrollState = rememberScrollState(),
    header: HeaderStyle = HeaderStyle.Light(0),
    headerPhoto: ImageBitmap? = null,
) {
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
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
            ListRow(title = stringResource(R.string.you_saved), icon = BleskIcons.Bookmark, onClick = actions.onOpenSaved)
            ListRow(title = stringResource(R.string.you_calls), icon = BleskIcons.Phone, soon = true)
            ListRow(title = stringResource(R.string.you_invites), icon = BleskIcons.Invite, soon = true)
            ListRow(title = stringResource(R.string.you_stickers), icon = BleskIcons.Sticker, soon = true)
            if (serverHost != null) {
                ListRow(
                    title = stringResource(R.string.settings_bots),
                    subtitle = stringResource(R.string.settings_bots_hint),
                    icon = BleskIcons.Bot,
                    onClick = actions.onOpenBots,
                    modifier = Modifier.sharedBounds(screenKey("bots")),
                )
            }
        }
    }
}

/** The «Настройки» tab: everything that is set up once and rarely touched. */
@Composable
fun SettingsList(
    serverHost: String?,
    debugUnlocked: Boolean,
    contentPadding: PaddingValues,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    sendByEnter: Boolean = false,
    onSendByEnter: (Boolean) -> Unit = {},
    vibration: VibrationLevel = VibrationLevel.Normal,
    onVibration: (VibrationLevel) -> Unit = {},
    doubleTap: String = GLINT_REACTION,
    onDoubleTap: (String) -> Unit = {},
) {
    val cardPadding = Modifier.padding(horizontal = Blesk.dimens.gutter)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        Card(cardPadding) {
            ListRow(
                title = stringResource(R.string.settings_appearance),
                subtitle = stringResource(R.string.settings_appearance_hint),
                icon = BleskIcons.Appearance,
                onClick = actions.onOpenAppearance,
                modifier = Modifier.sharedBounds(screenKey("appearance")),
            )
            ListRow(
                title = stringResource(R.string.settings_privacy),
                subtitle = stringResource(R.string.settings_privacy_hint),
                icon = BleskIcons.Shield,
                onClick = actions.onOpenPrivacy,
                modifier = Modifier.sharedBounds(screenKey("privacy")),
            )
            ListRow(
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_hint),
                icon = BleskIcons.Bell,
                onClick = actions.onOpenNotifications,
                modifier = Modifier.sharedBounds(screenKey("notifications")),
            )
            ListRow(
                title = stringResource(R.string.settings_storage),
                subtitle = stringResource(R.string.settings_storage_hint),
                icon = BleskIcons.Storage,
                onClick = actions.onOpenStorage,
                modifier = Modifier.sharedBounds(screenKey("storage")),
            )
        }
        SectionHeader(stringResource(R.string.settings_section_chats))
        Card(cardPadding) {
            ToggleRow(
                title = stringResource(R.string.settings_send_by_enter),
                subtitle = stringResource(R.string.settings_send_by_enter_hint),
                checked = sendByEnter,
                onCheckedChange = onSendByEnter,
                icon = BleskIcons.Send,
            )
            Text(
                text = stringResource(R.string.settings_double_tap),
                style = Blesk.type.bodyStrong,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 10.dp),
            )
            Segmented(
                options = DoubleTapChoices.map { if (it == GLINT_REACTION) stringResource(R.string.settings_double_tap_glint) else it }
                    .toImmutableList(),
                selected = DoubleTapChoices.indexOf(doubleTap).coerceAtLeast(0),
                onSelect = { onDoubleTap(DoubleTapChoices[it]) },
                modifier = Modifier.padding(bottom = 8.dp),
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
                modifier = Modifier.sharedBounds(screenKey("server")),
            )
        }
        Card(cardPadding.padding(top = Blesk.dimens.sectionGap)) {
            ListRow(
                title = stringResource(R.string.settings_about),
                icon = BleskIcons.Info,
                onClick = actions.onOpenAbout,
                modifier = Modifier.sharedBounds(screenKey("about")),
            )
            if (debugUnlocked) {
                ListRow(title = stringResource(R.string.settings_debug), icon = BleskIcons.Debug, onClick = actions.onOpenDebug)
            }
        }
    }
}

private val DoubleTapChoices = listOf(GLINT_REACTION, "\u2764\uFE0F", "\uD83D\uDC4D", "\uD83D\uDD25")
