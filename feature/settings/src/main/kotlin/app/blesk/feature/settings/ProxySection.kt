package app.blesk.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.ProxySetting
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

/** What a proxy check found: the server answered (in [millis], when there is a server to ask) or nothing did. */
sealed interface ProxyCheck {
    data class Works(val millis: Long?) : ProxyCheck

    data object Silent : ProxyCheck
}

/**
 * «Прокси»: SOCKS5 or HTTP, address and port, and a check that goes all the way to the server.
 * [heading] is left out where a row of that name already opens the section.
 */
@Composable
fun ProxySection(
    proxy: ProxySetting,
    onSave: (ProxySetting) -> Unit,
    onCheck: suspend (ProxySetting) -> ProxyCheck,
    heading: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val host = rememberTextFieldState(proxy.host)
    val port = rememberTextFieldState(proxy.port.takeIf { it > 0 }?.toString().orEmpty())
    var socks by remember { mutableStateOf(proxy.socks) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ProxyCheck?>(null) }
    fun edited(enabled: Boolean = proxy.enabled) = ProxySetting(
        enabled = enabled,
        socks = socks,
        host = host.text.toString().trim(),
        port = port.text.toString().toIntOrNull() ?: 0,
    )

    if (heading) SectionHeader(stringResource(R.string.proxy_title))
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        ToggleRow(
            title = stringResource(R.string.proxy_enabled),
            subtitle = stringResource(R.string.proxy_enabled_hint),
            icon = BleskIcons.Shield,
            checked = proxy.enabled,
            onCheckedChange = { onSave(edited(enabled = it)) },
        )
    }
    Segmented(
        options = persistentListOf(stringResource(R.string.proxy_socks), stringResource(R.string.proxy_http)),
        selected = if (socks) 0 else 1,
        onSelect = { socks = it == 0 },
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 12.dp),
    )
    TextInput(
        label = stringResource(R.string.proxy_host),
        state = host,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
    )
    TextInput(
        label = stringResource(R.string.proxy_port),
        state = port,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    Button(
        text = stringResource(if (checking) R.string.proxy_checking else R.string.proxy_save_check),
        kind = ButtonKind.Secondary,
        onClick = {
            if (checking) return@Button
            val setting = edited()
            onSave(setting)
            checking = true
            scope.launch {
                result = onCheck(setting)
                checking = false
                if (result is ProxyCheck.Works) haptics.success() else haptics.refuse()
            }
        },
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp).fillMaxWidth(),
    )
    val outcome = when (val found = result) {
        is ProxyCheck.Works -> found.millis?.let { stringResource(R.string.proxy_works_ms, it) } ?: stringResource(R.string.proxy_works)
        ProxyCheck.Silent -> stringResource(R.string.proxy_silent)
        null -> stringResource(R.string.proxy_hint)
    }
    Text(
        text = outcome,
        style = Blesk.type.label,
        color = if (result == ProxyCheck.Silent) Blesk.colors.danger else Blesk.colors.textSecondary,
        modifier = Modifier
            .padding(horizontal = Blesk.dimens.gutter + 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
