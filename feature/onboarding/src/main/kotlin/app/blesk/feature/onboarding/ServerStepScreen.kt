package app.blesk.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics

/** A server used before: [name] is shown, [link] goes into the field with its key, as its QR code had it. */
@Immutable
data class KnownServer(val name: String, val link: String)

/**
 * What the address step shows: the [last] server used, whether a server other than blesk's own is
 * [custom]-chosen now, and the check of the typed address.
 */
@Immutable
data class ServerStepState(
    val last: KnownServer? = null,
    val custom: Boolean = false,
    val busy: Boolean = false,
    val problem: AccessProblem? = null,
)

/**
 * The address step's actions: [onDone] checks the typed address, [onBlesk] goes back to blesk's
 * own server, [onUpdate] opens the app's update under «Приложение устарело».
 */
@Stable
class ServerStepActions(
    val onDone: () -> Unit,
    val onBack: () -> Unit,
    val onBlesk: () -> Unit = {},
    val onUpdate: (() -> Unit)? = null,
)

/**
 * «Другой сервер»: the address of a server other than blesk's own, typed, pasted as the link its
 * setup printed, or read from its QR code with [scanner]. [proxy] holds the proxy settings, for a
 * server reachable only through one; it opens under its row.
 */
@Composable
fun ServerStepScreen(
    address: TextFieldState,
    state: ServerStepState,
    actions: ServerStepActions,
    modifier: Modifier = Modifier,
    scanner: (@Composable (onCode: (String) -> Unit) -> Unit)? = null,
    proxy: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    var scanning by rememberSaveable { mutableStateOf(false) }
    ScrollScreen(
        title = stringResource(R.string.onboarding_server_title),
        onBack = actions.onBack,
        modifier = modifier,
        bottom = { StepActions(StepButton(stringResource(R.string.onboarding_done), actions.onDone, busy = state.busy)) },
    ) {
        StepText(stringResource(R.string.onboarding_server_text))
        TextInput(
            label = stringResource(R.string.onboarding_server_address),
            state = address,
            hint = stringResource(R.string.onboarding_server_address_hint),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            onKeyboardAction = { actions.onDone() },
        )
        StatusLine(
            problem = state.problem,
            busy = state.busy,
            busyText = stringResource(R.string.onboarding_connecting),
            onUpdate = actions.onUpdate,
        )
        if (scanner != null) {
            Button(
                text = stringResource(if (scanning) R.string.onboarding_server_scan_stop else R.string.onboarding_server_scan),
                onClick = { scanning = !scanning },
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 6.dp),
                kind = ButtonKind.Secondary,
                icon = BleskIcons.Qr,
            )
            AnimatedVisibility(scanning) {
                Box(
                    Modifier
                        .padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(Blesk.dimens.cardRadius)),
                ) {
                    scanner { code ->
                        address.setTextAndPlaceCursorAtEnd(code)
                        haptics.tick()
                        scanning = false
                    }
                }
            }
        }
        state.last?.let { last ->
            LastServer(last.name) {
                haptics.tick()
                address.setTextAndPlaceCursorAtEnd(last.link)
            }
        }
        if (proxy != null) Proxy(proxy)
        if (state.custom) {
            LinkRow(
                text = stringResource(R.string.onboarding_server_blesk),
                icon = BleskIcons.Server,
                onClick = actions.onBlesk,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter - 4.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * «Последний: …», a capsule that puts that server back into the field. A long address wraps
 * whole rather than losing its end, the part that tells one server from another; the corners
 * stay those of a one-line capsule.
 */
@Composable
private fun LastServer(name: String, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = Blesk.dimens.gutter, vertical = 6.dp)
            .heightIn(min = Blesk.dimens.touchTarget)
            .clip(RoundedCornerShape(Blesk.dimens.touchTarget / 2))
            .background(Blesk.colors.surfaceStrong)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val style = Blesk.type.label
        val iconSize = with(LocalDensity.current) { style.lineHeight.toDp() }
        Icon(BleskIcons.Clock, contentDescription = null, tint = Blesk.colors.textSecondary, size = iconSize)
        Text(stringResource(R.string.onboarding_server_last, name), style = style, modifier = Modifier.padding(vertical = 8.dp))
    }
}

/** The «Прокси» row and, once it is opened, the proxy settings under it. */
@Composable
private fun Proxy(settings: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) QUARTER_TURN else 0f, Blesk.motion.select(), label = "proxyChevron")
    ListRow(
        title = stringResource(R.string.onboarding_server_proxy),
        subtitle = stringResource(R.string.onboarding_server_proxy_hint),
        icon = BleskIcons.Shield,
        onClick = { open = !open },
        // TalkBack hears whether the settings are open, and can open or close them.
        modifier = Modifier.semantics {
            if (open) {
                collapse {
                    open = false
                    true
                }
            } else {
                expand {
                    open = true
                    true
                }
            }
        },
        trailing = {
            Icon(
                BleskIcons.ChevronRight,
                contentDescription = null,
                tint = Blesk.colors.textTertiary,
                size = 20.dp,
                modifier = Modifier.rotate(turn),
            )
        },
    )
    AnimatedVisibility(open) { Column { settings() } }
}

private const val QUARTER_TURN = 90f
