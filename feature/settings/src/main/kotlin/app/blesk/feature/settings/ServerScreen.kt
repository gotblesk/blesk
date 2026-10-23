package app.blesk.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.ProxySetting
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.shared.protocol.Invites
import kotlinx.coroutines.launch

/** How an attempt to join a server ended. */
enum class ConnectOutcome { Ok, BadAddress, NameTaken, NameInvalid, InviteRequired, InviteInvalid, TooManyAttempts, Unreachable }

/** What was filled in to create an account; [address] is null for blesk's own server. */
data class ConnectRequest(val address: String?, val invite: String?, val username: String)

/** What the screen shows about the current connection. */
data class ServerState(val url: String, val username: String, val pinned: Boolean)

@Composable
fun ServerScreen(
    connected: ServerState?,
    onConnect: suspend (ConnectRequest) -> ConnectOutcome,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    proxy: ProxyControls? = null,
    /** An invite code that came by link before there was an account. */
    pendingInvite: String? = null,
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
            ConnectForm(pendingInvite, onConnect)
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

/**
 * Registration on blesk's own server by default: an invite code, a username and «Создать аккаунт».
 * Another server is one small link away, so its address never stands in the way of everyone else.
 */
@Composable
private fun ConnectForm(pendingInvite: String?, onConnect: suspend (ConnectRequest) -> ConnectOutcome) {
    val scope = rememberCoroutineScope()
    var otherServer by rememberSaveable { mutableStateOf(false) }
    val address = rememberTextFieldState()
    val invite = rememberTextFieldState(pendingInvite.orEmpty())
    val username = rememberTextFieldState()
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
    LaunchedEffect(pendingInvite) { pendingInvite?.let(invite::setTextAndPlaceCursorAtEnd) }
    // A pasted link, or a whole message with one, turns into its code, so the field shows exactly what the server will check.
    LaunchedEffect(invite) {
        snapshotFlow { invite.text.toString() }.collect { text ->
            Invites.find(text)?.takeIf { it != text }?.let(invite::setTextAndPlaceCursorAtEnd)
        }
    }

    Text(
        text = stringResource(R.string.server_demo_text),
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp),
    )
    Row(
        modifier = Modifier
            .padding(horizontal = Blesk.dimens.gutter - 4.dp)
            .heightIn(min = Blesk.dimens.touchTarget)
            .clip(RoundedCornerShape(Blesk.dimens.cardRadius))
            .clickable(role = Role.Button) { otherServer = !otherServer }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(BleskIcons.Server, contentDescription = null, tint = Blesk.colors.textSecondary, size = 18.dp)
        Text(
            text = stringResource(if (otherServer) R.string.server_default else R.string.server_other),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
        )
    }
    AnimatedVisibility(otherServer) { OtherServer(address) }
    TextInput(
        label = stringResource(R.string.server_invite),
        state = invite,
        hint = stringResource(R.string.server_invite_hint),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
        ),
    )
    TextInput(
        label = stringResource(R.string.server_username),
        state = username,
        hint = stringResource(R.string.server_username_hint),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
    )
    ConnectStatus(busy, outcome, otherServer)
    Button(
        text = stringResource(R.string.server_create),
        onClick = {
            if (busy) return@Button
            val typed = invite.text.toString().takeIf { it.isNotBlank() }
            val request = ConnectRequest(
                address = address.text.toString().trim().takeIf { otherServer },
                invite = typed?.let(Invites::find),
                username = username.text.toString().trim().removePrefix("@").lowercase(),
            )
            outcome = when {
                // An empty address with «Другой сервер» open must not quietly become blesk's own server.
                request.address?.isEmpty() == true -> ConnectOutcome.BadAddress
                // A code that cannot be one is refused here; the server would only say the same later.
                typed != null && request.invite == null -> ConnectOutcome.InviteInvalid
                else -> null
            }
            if (outcome != null) return@Button
            busy = true
            scope.launch {
                outcome = onConnect(request)
                busy = false
            }
        },
        modifier = Modifier.padding(Blesk.dimens.gutter).fillMaxWidth(),
    )
}

/** The address of a server other than blesk's own: typed in, or read from the QR code its setup printed. */
@Composable
private fun OtherServer(address: TextFieldState) {
    var scanning by remember { mutableStateOf(false) }
    Column {
        TextInput(
            label = stringResource(R.string.server_address),
            state = address,
            hint = stringResource(R.string.server_address_hint),
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
    }
}

/** «Создаём…» while it works, then why it did not; read out by TalkBack as it changes. */
@Composable
private fun ConnectStatus(busy: Boolean, outcome: ConnectOutcome?, otherServer: Boolean) {
    val error = when (outcome) {
        ConnectOutcome.BadAddress -> R.string.server_error_address
        ConnectOutcome.NameTaken -> R.string.server_error_taken
        ConnectOutcome.NameInvalid -> R.string.server_error_invalid
        ConnectOutcome.InviteRequired -> R.string.server_error_invite_required
        ConnectOutcome.InviteInvalid -> R.string.server_error_invite_invalid
        ConnectOutcome.TooManyAttempts -> R.string.server_error_too_many
        ConnectOutcome.Unreachable -> if (otherServer) R.string.server_error_unreachable_other else R.string.server_error_unreachable
        ConnectOutcome.Ok, null -> null
    }
    val status = if (busy) R.string.server_creating else error ?: return
    Text(
        text = stringResource(status),
        style = Blesk.type.label,
        color = if (busy) Blesk.colors.textSecondary else Blesk.colors.danger,
        modifier = Modifier
            .padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
