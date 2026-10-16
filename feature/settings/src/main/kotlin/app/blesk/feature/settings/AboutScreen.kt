package app.blesk.feature.settings

import android.content.ClipData
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.launch

/** Taps on the version row needed to unlock the debug screen. */
const val DEBUG_UNLOCK_TAPS = 7

/** How many remaining taps are announced; the first few taps stay silent. */
private const val ANNOUNCE_FROM = 4

/** Where this copy of blesk comes from, judged by its signing key against the published ones. */
enum class SignedBy { Official, Test, Unofficial }

/** «Подлинность» in «О приложении»: the verdict and the key's SHA-256. */
class BuildSignature(val by: SignedBy, val fingerprint: String)

@Composable
fun AboutScreen(
    versionName: String,
    debugUnlocked: Boolean,
    onUnlockDebug: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPolicy: () -> Unit,
    onBack: () -> Unit,
    onOpenSandbox: () -> Unit = {},
    modifier: Modifier = Modifier,
    updates: UpdateControls? = null,
    signature: BuildSignature? = null,
) {
    val uriHandler = LocalUriHandler.current
    val source = stringResource(R.string.about_source_url)
    var taps by remember { mutableIntStateOf(0) }
    val remaining = DEBUG_UNLOCK_TAPS - taps
    val play = stringResource(R.string.sandbox_title)
    ScrollScreen(title = stringResource(R.string.about_title), onBack = onBack, modifier = modifier) {
        // The wordmark hides the metaball sandbox behind a long press; TalkBack offers it as an action.
        Text(
            text = stringResource(R.string.about_wordmark),
            style = Blesk.type.display,
            modifier = Modifier
                .padding(horizontal = Blesk.dimens.gutter + 4.dp)
                .padding(top = 8.dp, bottom = 12.dp)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onOpenSandbox() }) }
                .semantics { customActions = listOf(CustomAccessibilityAction(play) { onOpenSandbox(); true }) },
        )
        Text(
            text = stringResource(R.string.about_tagline),
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).padding(bottom = Blesk.dimens.sectionGap),
        )
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
            ListRow(
                title = stringResource(R.string.about_version),
                subtitle = versionName,
                showChevron = false,
                onClick = {
                    if (debugUnlocked) return@ListRow
                    taps++
                    if (taps >= DEBUG_UNLOCK_TAPS) onUnlockDebug()
                },
            )
            signature?.let { SignatureRow(it) }
            if (updates != null) {
                ListRow(title = stringResource(R.string.about_whats_new), onClick = updates.onOpenWhatsNew)
                ToggleRow(
                    title = stringResource(R.string.about_updates),
                    subtitle = stringResource(R.string.about_updates_hint),
                    checked = updates.checking,
                    onCheckedChange = updates.onChecking,
                )
            }
            ListRow(
                title = stringResource(R.string.about_licenses),
                subtitle = stringResource(R.string.about_license_app),
                onClick = onOpenLicenses,
            )
            ListRow(
                title = stringResource(R.string.about_policy),
                subtitle = stringResource(R.string.about_policy_hint),
                onClick = onOpenPolicy,
            )
            ListRow(
                title = stringResource(R.string.about_source),
                subtitle = source.removePrefix("https://"),
                onClick = { uriHandler.openUri(source) },
            )
        }
        val hint = when {
            debugUnlocked && taps > 0 -> stringResource(R.string.about_debug_on)
            !debugUnlocked && remaining in 1..ANNOUNCE_FROM -> stringResource(R.string.about_debug_steps, remaining)
            else -> null
        }
        if (hint != null) {
            Text(
                text = hint,
                style = Blesk.type.label,
                color = Blesk.colors.textTertiary,
                modifier = Modifier
                    .padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** «Что нового» and whether the app looks for newer builds. */
class UpdateControls(val checking: Boolean, val onChecking: (Boolean) -> Unit, val onOpenWhatsNew: () -> Unit)

/** The verdict, and under it the key's SHA-256 in groups of four, copied with a tap for comparing with `README.md`. */
@Composable
private fun SignatureRow(signature: BuildSignature) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val notify = rememberNotify()
    val copied = stringResource(R.string.about_fingerprint_copied)
    val verdict = stringResource(
        when (signature.by) {
            SignedBy.Official -> R.string.about_official
            SignedBy.Test -> R.string.about_test_build
            SignedBy.Unofficial -> R.string.about_unofficial
        },
    )
    val grouped = signature.fingerprint.uppercase().chunked(FINGERPRINT_GROUP).joinToString(" ")
    ListRow(
        title = stringResource(R.string.about_authenticity),
        subtitle = "$verdict\n$grouped",
        icon = if (signature.by == SignedBy.Official) BleskIcons.Shield else BleskIcons.Alert,
        showChevron = false,
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("blesk", signature.fingerprint)))
                notify(copied)
            }
        },
    )
}

private const val FINGERPRINT_GROUP = 4
