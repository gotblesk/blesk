package app.blesk.feature.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.Profile
import app.blesk.core.data.settings.VibrationLevel
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Capsule
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.DemoTag
import app.blesk.core.design.component.Icon
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
) {
    val cardPadding = Modifier.padding(horizontal = Blesk.dimens.gutter)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        Capsule(
            modifier = cardPadding.padding(top = 4.dp).fillMaxWidth(),
            onClick = actions.onOpenProfile,
            onClickLabel = stringResource(R.string.settings_profile),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Avatar(profile.name.ifBlank { "?" }, size = 60.dp, photo = photo, key = profile.username.ifBlank { profile.name })
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (profile.isEmpty) stringResource(R.string.settings_profile) else profile.name,
                            style = Blesk.type.headline,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (demo) DemoTag()
                    }
                    Text(
                        text = if (profile.username.isNotBlank()) {
                            "@${profile.username}"
                        } else {
                            stringResource(R.string.settings_profile_setup)
                        },
                        style = Blesk.type.label,
                        color = Blesk.colors.textSecondary,
                        maxLines = 1,
                    )
                    if (profile.bio.isNotBlank()) {
                        Text(profile.bio, style = Blesk.type.label, color = Blesk.colors.textTertiary, maxLines = 2)
                    }
                }
                Icon(BleskIcons.Qr, contentDescription = null, tint = Blesk.colors.textSecondary)
            }
        }
        Card(cardPadding.padding(top = Blesk.dimens.sectionGap)) {
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
