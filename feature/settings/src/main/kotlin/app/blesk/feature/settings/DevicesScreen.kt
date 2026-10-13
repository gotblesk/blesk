package app.blesk.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk

/** What is known about this phone without asking anyone: its model and the versions on it. */
class ThisDevice(val model: String, val android: String, val app: String)

/**
 * «Ты → Устройства»: this phone and the sign-ins to the account. The address, the city and the
 * history of sign-ins come from the server, so the demo says that instead of showing made-up ones.
 */
@Composable
fun DevicesScreen(device: ThisDevice, demo: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val needsServer = stringResource(R.string.needs_server)
    ScrollScreen(title = stringResource(R.string.you_devices), onBack = onBack, modifier = modifier) {
        SectionHeader(stringResource(R.string.devices_this))
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
            Fact(stringResource(R.string.devices_model), device.model)
            Fact(stringResource(R.string.devices_android), device.android)
            Fact(stringResource(R.string.devices_app), device.app)
            Fact(stringResource(R.string.devices_active), stringResource(R.string.devices_active_now))
            if (demo) {
                Fact(stringResource(R.string.devices_address), needsServer)
            } else {
                ListRow(title = stringResource(R.string.devices_address), icon = BleskIcons.Shield, soon = true)
            }
        }
        SectionHeader(stringResource(R.string.devices_history))
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
            if (demo) {
                ListRow(title = needsServer, icon = BleskIcons.Clock, showChevron = false)
            } else {
                ListRow(title = stringResource(R.string.devices_history), icon = BleskIcons.Clock, soon = true)
            }
        }
        Text(
            text = stringResource(R.string.devices_privacy),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Fact(title: String, value: String) {
    ListRow(title = title, subtitle = value, showChevron = false)
}
