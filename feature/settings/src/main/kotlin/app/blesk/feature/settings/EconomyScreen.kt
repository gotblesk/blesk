package app.blesk.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.blesk.core.data.settings.Economy
import app.blesk.core.data.settings.EconomyMode
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Slider
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.roundToInt

/**
 * «Экономный режим»: off, by itself or always; the charge it comes on below; what it simplifies,
 * each on its own switch; whether to offer it on low charge; and the way to the phone's own
 * power saving, which an app is not allowed to turn on.
 */
@Composable
fun EconomyScreen(
    economy: Economy,
    /** Whether it is on right now, and at what charge. */
    active: Boolean,
    level: Int,
    onChange: (Economy) -> Unit,
    onOpenSystemSaver: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    ScrollScreen(title = stringResource(R.string.economy_title), onBack = onBack, modifier = modifier) {
        Segmented(
            options = persistentListOf(
                stringResource(R.string.economy_off),
                stringResource(R.string.economy_auto),
                stringResource(R.string.economy_always),
            ),
            selected = economy.mode.ordinal,
            onSelect = { onChange(economy.copy(mode = EconomyMode.entries[it])) },
        )
        val hint = when (economy.mode) {
            EconomyMode.Off -> R.string.economy_mode_hint_off
            EconomyMode.Auto -> R.string.economy_mode_hint_auto
            EconomyMode.Always -> R.string.economy_mode_hint_always
        }
        Text(
            text = stringResource(hint),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = pad.padding(top = Blesk.dimens.rowGap),
        )
        if (active) {
            Text(
                text = stringResource(R.string.economy_active, level),
                style = Blesk.type.label,
                color = Blesk.colors.textPrimary,
                modifier = pad.padding(top = Blesk.dimens.rowGap),
            )
        }
        Slider(
            title = stringResource(R.string.economy_threshold),
            value = economy.threshold.toFloat(),
            onValueChange = { onChange(economy.copy(threshold = it.roundToInt())) },
            range = Economy.MIN_THRESHOLD.toFloat()..Economy.MAX_THRESHOLD.toFloat(),
            valueLabel = stringResource(R.string.economy_threshold_value, economy.threshold),
            modifier = Modifier.padding(top = Blesk.dimens.sectionGap),
        )
        SectionHeader(stringResource(R.string.economy_what))
        Card(pad) {
            ToggleRow(stringResource(R.string.economy_matte), economy.matteGlass, { onChange(economy.copy(matteGlass = it)) })
            ToggleRow(stringResource(R.string.economy_still_light), economy.stillLight, { onChange(economy.copy(stillLight = it)) })
            ToggleRow(stringResource(R.string.economy_simple_motion), economy.simpleMotion, { onChange(economy.copy(simpleMotion = it)) })
            ToggleRow(stringResource(R.string.economy_tap_to_play), economy.tapToPlay, { onChange(economy.copy(tapToPlay = it)) })
            ToggleRow(stringResource(R.string.economy_wifi_only), economy.wifiOnlyMedia, { onChange(economy.copy(wifiOnlyMedia = it)) })
            ToggleRow(stringResource(R.string.economy_60hz), economy.cap60Hz, { onChange(economy.copy(cap60Hz = it)) })
        }
        Card(pad.padding(top = Blesk.dimens.sectionGap)) {
            ToggleRow(
                title = stringResource(R.string.economy_offer_toggle),
                subtitle = stringResource(R.string.economy_offer_toggle_hint),
                checked = economy.offer,
                onCheckedChange = { onChange(economy.copy(offer = it)) },
            )
            ListRow(
                title = stringResource(R.string.economy_system),
                subtitle = stringResource(R.string.economy_system_hint),
                icon = BleskIcons.Battery,
                onClick = onOpenSystemSaver,
            )
        }
    }
}
