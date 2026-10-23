package app.blesk.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.ProxySetting
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics

/** What the screen shows about the current connection. */
data class ServerState(val url: String, val username: String, val pinned: Boolean)

/**
 * The way to an account from the demo. [server] names the server chosen on «Другой сервер» when it
 * is not blesk's own; [problem] says why the last look at it failed, and [onUpdate] opens the
 * app's update when the server serves only newer versions.
 */
@Stable
class DemoSignUp(
    val server: String?,
    val busy: Boolean,
    val problem: String?,
    val onCreate: () -> Unit,
    val onOtherServer: () -> Unit,
    val onUpdate: (() -> Unit)? = null,
)

/** The server the account is on, or [signUp] while there is no account; and the proxy either way. */
@Composable
fun ServerScreen(
    connected: ServerState?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    proxy: ProxyControls? = null,
    signUp: DemoSignUp? = null,
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
        } else if (signUp != null) {
            DemoCard(signUp)
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
 * «Создать аккаунт» on blesk's own server, or on the one chosen; signing in by the phrase, still to
 * come; and another server one row away, so its address never stands in everyone else's way.
 */
@Composable
private fun DemoCard(signUp: DemoSignUp) {
    val haptics = rememberHaptics()
    // Hidden while a new look runs, so the same problem coming back is refused again.
    val problem = signUp.problem.takeIf { !signUp.busy }
    LaunchedEffect(problem) { if (problem != null) haptics.refuse() }
    Text(
        text = stringResource(R.string.server_demo_text),
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp),
    )
    Button(
        text = stringResource(R.string.server_create),
        onClick = signUp.onCreate,
        modifier = Modifier.padding(Blesk.dimens.gutter).fillMaxWidth(),
        busy = signUp.busy,
    )
    if (problem != null) {
        Problem(problem)
        signUp.onUpdate?.let { update ->
            Button(
                text = stringResource(R.string.server_update),
                onClick = update,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp).fillMaxWidth(),
                kind = ButtonKind.Secondary,
            )
        }
    }
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = 12.dp)) {
        ListRow(title = stringResource(R.string.server_sign_in), icon = BleskIcons.Lock, soon = true)
        ListRow(
            title = stringResource(R.string.server_other),
            subtitle = signUp.server?.let { stringResource(R.string.server_chosen, it) } ?: stringResource(R.string.server_other_hint),
            icon = BleskIcons.Server,
            onClick = signUp.onOtherServer,
        )
    }
}

/** Why the server could not be used: in the colour of danger, never only in it, and read out by TalkBack. */
@Composable
private fun Problem(text: String) {
    Row(
        Modifier
            .padding(horizontal = Blesk.dimens.gutter + 4.dp)
            .padding(bottom = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val style = Blesk.type.label
        val iconSize = with(LocalDensity.current) { style.lineHeight.toDp() }
        Icon(BleskIcons.Alert, contentDescription = null, tint = Blesk.colors.danger, size = iconSize)
        Text(text, style = style, color = Blesk.colors.danger)
    }
}
