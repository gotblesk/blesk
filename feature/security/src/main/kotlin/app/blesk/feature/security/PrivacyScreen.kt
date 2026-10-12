package app.blesk.feature.security

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.Audience
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

class PrivacyActions(
    val onChange: (Privacy) -> Unit,
    val onEnablePin: () -> Unit,
    val onDisablePin: () -> Unit,
    val onChangePin: () -> Unit,
    val onWipe: () -> Unit,
    val onBack: () -> Unit,
    /** People you blocked; null in the demo. */
    val onOpenBlocked: (() -> Unit)? = null,
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
        SectionHeader(stringResource(R.string.privacy_who))
        Card(pad) {
            Choice(
                title = stringResource(R.string.privacy_find),
                hint = stringResource(R.string.privacy_find_hint),
                options = persistentListOf(stringResource(R.string.privacy_everyone), stringResource(R.string.privacy_by_link)),
                selected = if (privacy.findable) 0 else 1,
                onSelect = { actions.onChange(privacy.copy(findable = it == 0)) },
            )
            AudienceChoice(stringResource(R.string.privacy_who_writes), privacy.write, withNobody = false) {
                actions.onChange(privacy.copy(write = it))
            }
            AudienceChoice(stringResource(R.string.privacy_who_calls), privacy.calls) { actions.onChange(privacy.copy(calls = it)) }
            AudienceChoice(stringResource(R.string.privacy_who_adds), privacy.groups) { actions.onChange(privacy.copy(groups = it)) }
            AudienceChoice(stringResource(R.string.privacy_who_sees_online), privacy.online) { actions.onChange(privacy.copy(online = it)) }
            AudienceChoice(stringResource(R.string.privacy_who_sees_photo), privacy.photo) { actions.onChange(privacy.copy(photo = it)) }
            actions.onOpenBlocked?.let { open ->
                ListRow(title = stringResource(R.string.privacy_blocked), icon = BleskIcons.Shield, onClick = open)
            }
        }

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
                ToggleRow(
                    title = stringResource(R.string.privacy_wipe_after_ten),
                    subtitle = stringResource(R.string.privacy_wipe_after_ten_hint),
                    checked = privacy.wipeAfterTen,
                    onCheckedChange = { actions.onChange(privacy.copy(wipeAfterTen = it)) },
                )
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
            ToggleRow(
                title = stringResource(R.string.privacy_link_previews),
                subtitle = stringResource(R.string.privacy_link_previews_hint),
                checked = privacy.linkPreviews,
                onCheckedChange = { actions.onChange(privacy.copy(linkPreviews = it)) },
            )
            ToggleRow(
                title = stringResource(R.string.privacy_incognito),
                subtitle = stringResource(R.string.privacy_incognito_hint),
                checked = privacy.incognitoKeyboard,
                onCheckedChange = { actions.onChange(privacy.copy(incognitoKeyboard = it)) },
            )
            Choice(
                title = stringResource(R.string.privacy_default_timer),
                hint = stringResource(R.string.privacy_default_timer_hint),
                options = persistentListOf(
                    stringResource(R.string.privacy_timer_off),
                    stringResource(R.string.privacy_timer_hour),
                    stringResource(R.string.privacy_timer_day),
                    stringResource(R.string.privacy_timer_week),
                ),
                selected = Privacy.TIMER_STEPS.indexOf(privacy.defaultTimerSeconds).coerceAtLeast(0),
                onSelect = { actions.onChange(privacy.copy(defaultTimerSeconds = Privacy.TIMER_STEPS[it])) },
            )
        }

        SectionHeader(stringResource(R.string.privacy_keys))
        Card(pad) {
            ListRow(title = stringResource(R.string.privacy_phrase), icon = BleskIcons.Lock, soon = true)
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

@Composable
private fun AudienceChoice(title: String, value: Audience, withNobody: Boolean = true, onSelect: (Audience) -> Unit) {
    val choices = if (withNobody) Audience.entries else listOf(Audience.Everyone, Audience.Contacts)
    Choice(
        title = title,
        options = choices.map { audience ->
            stringResource(
                when (audience) {
                    Audience.Everyone -> R.string.privacy_everyone
                    Audience.Contacts -> R.string.privacy_contacts
                    Audience.Nobody -> R.string.privacy_nobody
                },
            )
        }.toImmutableList(),
        selected = choices.indexOf(value).coerceAtLeast(0),
        onSelect = { onSelect(choices[it]) },
    )
}

@Composable
private fun Choice(title: String, options: ImmutableList<String>, selected: Int, onSelect: (Int) -> Unit, hint: String? = null) {
    Text(
        text = title,
        style = Blesk.type.bodyStrong,
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 12.dp),
    )
    hint?.let {
        Text(
            text = it,
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 2.dp),
        )
    }
    Segmented(options = options, selected = selected, onSelect = onSelect, modifier = Modifier.padding(bottom = 4.dp))
}
