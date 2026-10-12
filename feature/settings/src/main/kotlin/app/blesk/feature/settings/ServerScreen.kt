package app.blesk.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.ProxySetting
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.coroutines.launch

/** How an attempt to join a server ended. */
enum class ConnectOutcome { Ok, BadAddress, NameTaken, NameInvalid, Unreachable }

/** What the screen shows about the current connection. */
data class ServerState(val url: String, val username: String, val pinned: Boolean)

@Composable
fun ServerScreen(
    connected: ServerState?,
    onConnect: suspend (address: String, username: String) -> ConnectOutcome,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    proxy: ProxyControls? = null,
) {
    ScrollScreen(title = stringResource(R.string.server_title), onBack = onBack, modifier = modifier) {
        if (connected != null) {
            Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
                ListRow(
                    title = stringResource(R.string.server_connected),
                    subtitle = connected.url,
                    icon = BleskIcons.Server,
                    showChevron = false,
                )
                ListRow(
                    title = stringResource(R.string.server_connected_as, connected.username),
                    icon = BleskIcons.Person,
                    showChevron = false,
                )
                if (connected.pinned) ListRow(title = stringResource(R.string.server_pinned), icon = BleskIcons.Shield, showChevron = false)
            }
            Text(
                text = stringResource(R.string.server_change_hint),
                style = Blesk.type.label,
                color = Blesk.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 12.dp),
            )
        } else {
            ConnectForm(onConnect)
        }
        proxy?.let { ProxySection(it.setting, it.onSave, it.onCheck) }
    }
}

/** The proxy part of the screen: the saved setting, saving it, and checking it. */
class ProxyControls(
    val setting: ProxySetting,
    val onSave: (ProxySetting) -> Unit,
    val onCheck: suspend (ProxySetting) -> ProxyCheck,
)

@Composable
private fun ConnectForm(onConnect: suspend (String, String) -> ConnectOutcome) {
    val scope = rememberCoroutineScope()
    val address = rememberTextFieldState()
    val username = rememberTextFieldState()
    var scanning by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<ConnectOutcome?>(null) }
    val haptics = rememberHaptics()
    LaunchedEffect(outcome) {
        when (outcome) {
            ConnectOutcome.Ok -> haptics.success()
            null -> Unit
            else -> haptics.refuse()
        }
    }

    Text(
        text = stringResource(R.string.server_demo_text),
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).padding(bottom = 16.dp),
    )
    TextInput(
        label = stringResource(R.string.server_address),
        state = address,
        hint = if (outcome == ConnectOutcome.BadAddress) {
            stringResource(R.string.server_error_address)
        } else {
            stringResource(R.string.server_address_hint)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
    )
    Button(
        text = stringResource(if (scanning) R.string.server_scan_stop else R.string.server_scan),
        icon = BleskIcons.Qr,
        kind = ButtonKind.Secondary,
        onClick = { scanning = !scanning },
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 6.dp),
    )
    AnimatedVisibility(scanning) {
        QrScanner(
            onCode = { code ->
                address.setTextAndPlaceCursorAtEnd(code)
                scanning = false
            },
            modifier = Modifier
                .padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Blesk.dimens.cardRadius)),
        )
    }
    TextInput(
        label = stringResource(R.string.server_username),
        state = username,
        hint = stringResource(R.string.server_username_hint),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
    )
    val error = when (outcome) {
        ConnectOutcome.NameTaken -> stringResource(R.string.server_error_taken)
        ConnectOutcome.NameInvalid -> stringResource(R.string.server_error_invalid)
        ConnectOutcome.Unreachable -> stringResource(R.string.server_error_unreachable)
        else -> null
    }
    val status = if (busy) stringResource(R.string.server_creating) else error
    if (status != null) {
        Text(
            text = status,
            style = Blesk.type.label,
            color = if (busy) Blesk.colors.textSecondary else Blesk.colors.danger,
            modifier = Modifier
                .padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Button(
        text = stringResource(R.string.server_create),
        onClick = {
            if (busy) return@Button
            busy = true
            outcome = null
            scope.launch {
                outcome = onConnect(address.text.toString(), username.text.toString().trim().removePrefix("@").lowercase())
                busy = false
            }
        },
        modifier = Modifier.padding(Blesk.dimens.gutter).fillMaxWidth(),
    )
}
