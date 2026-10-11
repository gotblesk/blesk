package app.blesk.feature.security

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.LockDelay
import app.blesk.core.data.settings.Privacy
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.persistentListOf

class PrivacyActions(
    val onChange: (Privacy) -> Unit,
    val onEnablePin: () -> Unit,
    val onDisablePin: () -> Unit,
    val onChangePin: () -> Unit,
    val onWipe: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
fun PrivacyScreen(
    privacy: Privacy,
    pinSet: Boolean,
    biometricAvailable: Boolean,
    actions: PrivacyActions,
    modifier: Modifier = Modifier,
) {
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    ScrollScreen(title = stringResource(R.string.privacy_title), onBack = actions.onBack, modifier = modifier) {
        SectionHeader(stringResource(R.string.privacy_lock))
        Card(pad) {
            ToggleRow(
                title = stringResource(R.string.privacy_pin),
                subtitle = stringResource(R.string.privacy_pin_hint),
                icon = BleskIcons.Lock,
                checked = pinSet,
                onCheckedChange = { if (it) actions.onEnablePin() else actions.onDisablePin() },
            )
            if (pinSet) {
                ListRow(title = stringResource(R.string.privacy_change_pin), onClick = actions.onChangePin)
                if (biometricAvailable) {
                    ToggleRow(
                        title = stringResource(R.string.privacy_biometric),
                        icon = BleskIcons.Fingerprint,
                        checked = privacy.biometric,
                        onCheckedChange = { actions.onChange(privacy.copy(biometric = it)) },
                    )
                }
                Text(
                    text = stringResource(R.string.privacy_delay),
                    style = Blesk.type.bodyStrong,
                    modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 10.dp),
                )
                Segmented(
                    options = persistentListOf(
                        stringResource(R.string.privacy_delay_now),
                        stringResource(R.string.privacy_delay_minute),
                        stringResource(R.string.privacy_delay_quarter),
                        stringResource(R.string.privacy_delay_hour),
                    ),
                    selected = privacy.lockDelay.ordinal,
                    onSelect = { actions.onChange(privacy.copy(lockDelay = LockDelay.entries[it])) },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        SectionHeader(stringResource(R.string.privacy_screen))
        Card(pad) {
            ToggleRow(
                title = stringResource(R.string.privacy_secure),
                subtitle = stringResource(R.string.privacy_secure_hint),
                checked = privacy.secureScreen,
                onCheckedChange = { actions.onChange(privacy.copy(secureScreen = it)) },
            )
        }

        SectionHeader(stringResource(R.string.privacy_messages))
        Card(pad) {
            ToggleRow(
                title = stringResource(R.string.privacy_receipts),
                subtitle = stringResource(R.string.privacy_receipts_hint),
                checked = privacy.readReceipts,
                onCheckedChange = { actions.onChange(privacy.copy(readReceipts = it)) },
            )
            ToggleRow(
                title = stringResource(R.string.privacy_typing),
                checked = privacy.typingIndicator,
                onCheckedChange = { actions.onChange(privacy.copy(typingIndicator = it)) },
            )
        }

        Card(pad.padding(top = Blesk.dimens.sectionGap)) {
            ListRow(
                title = stringResource(R.string.privacy_wipe),
                subtitle = stringResource(R.string.privacy_wipe_hint),
                icon = BleskIcons.Trash,
                onClick = actions.onWipe,
            )
        }
    }
}
