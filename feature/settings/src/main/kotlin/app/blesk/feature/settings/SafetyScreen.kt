package app.blesk.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.KeyState
import app.blesk.core.data.SafetyCode
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.glint
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.launch

@Composable
fun SafetyScreen(
    name: String,
    code: SafetyCode?,
    state: KeyState,
    onScanned: suspend (String) -> Boolean,
    onSetVerified: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Draws a QR code of the given text; supplied by the app. */
    qr: @Composable BoxScope.(String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<Boolean?>(null) }
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    ScrollScreen(title = stringResource(R.string.safety_title), onBack = onBack, modifier = modifier) {
        Status(state, pad)
        if (code == null) {
            Text(stringResource(R.string.safety_none), color = Blesk.colors.textSecondary, modifier = pad.padding(top = 12.dp))
            return@ScrollScreen
        }
        Text(
            text = stringResource(R.string.safety_intro, name),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = pad.padding(vertical = 12.dp),
        )
        Digits(code.digits, pad)
        Box(
            Modifier
                .padding(Blesk.dimens.gutter)
                .align(Alignment.CenterHorizontally)
                .size(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(14.dp),
        ) { qr(code.scannable) }
        Text(
            text = stringResource(R.string.safety_qr),
            style = Blesk.type.caption,
            color = Blesk.colors.textTertiary,
            modifier = pad.align(Alignment.CenterHorizontally),
        )
        outcome?.let { matched ->
            Text(
                text = stringResource(if (matched) R.string.safety_match else R.string.safety_mismatch),
                color = if (matched) Blesk.colors.textPrimary else Blesk.colors.danger,
                modifier = pad.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        if (scanning) {
            QrScanner(
                onCode = { scanned ->
                    scanning = false
                    scope.launch { outcome = onScanned(scanned) }
                },
                modifier = pad.padding(top = 12.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)),
            )
        }
        Column(pad.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                text = stringResource(if (scanning) R.string.safety_scan_stop else R.string.safety_scan),
                icon = BleskIcons.Qr,
                onClick = { scanning = !scanning },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                text = stringResource(if (state.verified) R.string.safety_unmark else R.string.safety_mark),
                kind = ButtonKind.Secondary,
                onClick = { onSetVerified(!state.verified) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Status(state: KeyState, modifier: Modifier) {
    val shape = RoundedCornerShape(Blesk.dimens.capsuleRadius)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .clip(shape)
                .background(Blesk.colors.surface)
                .glint(shape, enabled = state.verified)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(BleskIcons.Shield, contentDescription = null, size = 18.dp, tint = Blesk.colors.textSecondary)
            val label = if (state.verified) R.string.safety_verified else R.string.safety_not_verified
            Text(stringResource(label), style = Blesk.type.bodyStrong)
        }
        if (state.changed) {
            Text(stringResource(R.string.safety_changed), color = Blesk.colors.danger, style = Blesk.type.label)
        }
    }
}

/** Twelve groups of five, three rows: easy to read aloud. */
@Composable
private fun Digits(digits: String, modifier: Modifier) {
    val groups = digits.chunked(GROUP)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Blesk.colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        groups.chunked(PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { group ->
                    Text(group, style = Blesk.type.headline.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
    }
}

private const val GROUP = 5
private const val PER_ROW = 4
