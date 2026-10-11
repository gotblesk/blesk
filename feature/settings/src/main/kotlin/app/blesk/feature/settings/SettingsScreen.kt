package app.blesk.feature.settings

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
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Capsule
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk

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

/** The settings tab, placed under the home lenses. */
@Composable
fun SettingsList(
    serverHost: String?,
    debugUnlocked: Boolean,
    profile: Profile,
    photo: ImageBitmap?,
    contentPadding: PaddingValues,
    actions: SettingsActions,
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
                Avatar(profile.name.ifBlank { "?" }, size = 60.dp, photo = photo)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (profile.isEmpty) stringResource(R.string.settings_profile) else profile.name,
                        style = Blesk.type.headline,
                        maxLines = 1,
                    )
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
                }
                Icon(BleskIcons.Qr, contentDescription = null, tint = Blesk.colors.textSecondary)
            }
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
                title = stringResource(R.string.settings_privacy),
                subtitle = stringResource(R.string.settings_privacy_hint),
                icon = BleskIcons.Lock,
                onClick = actions.onOpenPrivacy,
            )
            ListRow(
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_hint),
                icon = BleskIcons.Chats,
                onClick = actions.onOpenNotifications,
            )
            ListRow(
                title = stringResource(R.string.settings_storage),
                subtitle = stringResource(R.string.settings_storage_hint),
                icon = BleskIcons.Document,
                onClick = actions.onOpenStorage,
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
            if (serverHost != null) {
                ListRow(
                    title = stringResource(R.string.settings_bots),
                    subtitle = stringResource(R.string.settings_bots_hint),
                    icon = BleskIcons.Bot,
                    onClick = actions.onOpenBots,
                )
            }
        }
        Card(cardPadding.padding(top = Blesk.dimens.sectionGap)) {
            ListRow(title = stringResource(R.string.settings_about), icon = BleskIcons.Info, onClick = actions.onOpenAbout)
            if (debugUnlocked) {
                ListRow(title = stringResource(R.string.settings_debug), icon = BleskIcons.Debug, onClick = actions.onOpenDebug)
            }
        }
    }
}
