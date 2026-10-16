package app.blesk.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlin.math.roundToInt

/** Where installing an update is: not started, downloading, failed, or handed to the system installer. */
sealed interface UpdateProgress {
    data object Idle : UpdateProgress

    data class Downloading(val fraction: Float) : UpdateProgress

    data object NetworkFailed : UpdateProgress

    /** The file was not signed with blesk's key: it is deleted and never installed. */
    data object Forged : UpdateProgress

    /** The file is not the one published with the release: it is deleted and never installed. */
    data object Mismatch : UpdateProgress

    data object Handed : UpdateProgress
}

/** A newer build: what is in it and a button that downloads, checks the signature and installs it. */
@Composable
fun UpdateScreen(title: String, notes: String, progress: UpdateProgress, onInstall: () -> Unit, onBack: () -> Unit) {
    ScrollScreen(title = stringResource(R.string.update_title), onBack = onBack) {
        Text(
            text = title,
            style = Blesk.type.headline,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).semantics { heading() },
        )
        val status = when (progress) {
            UpdateProgress.Idle -> null
            is UpdateProgress.Downloading -> stringResource(R.string.update_downloading, (progress.fraction * PERCENT).roundToInt())
            UpdateProgress.NetworkFailed -> stringResource(R.string.update_failed)
            UpdateProgress.Forged -> stringResource(R.string.update_forged)
            UpdateProgress.Mismatch -> stringResource(R.string.update_mismatch)
            UpdateProgress.Handed -> stringResource(R.string.update_handed)
        }
        if (progress !is UpdateProgress.Downloading) {
            Button(
                text = stringResource(R.string.update_install),
                icon = BleskIcons.Download,
                onClick = onInstall,
                modifier = Modifier.padding(Blesk.dimens.gutter).fillMaxWidth(),
            )
        }
        if (status != null) {
            Text(
                text = status,
                style = Blesk.type.label,
                color = if (progress in REFUSED) Blesk.colors.danger else Blesk.colors.textSecondary,
                modifier = Modifier
                    .padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        ReleaseNotes(notes)
    }
}

/** «Что нового» for the running version, from the notes built into the app. */
@Composable
fun WhatsNewScreen(notes: String, onBack: () -> Unit) {
    ScrollScreen(title = stringResource(R.string.about_whats_new), onBack = onBack) {
        ReleaseNotes(notes)
    }
}

/** Release notes as written in `docs/RELEASE_NOTES.md`: headings, items and plain lines. */
@Composable
internal fun ReleaseNotes(notes: String) {
    Column(Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp)) {
        notes.lines().map(String::trim).filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("#") -> Text(
                    text = line.trimStart('#').trim(),
                    style = Blesk.type.bodyStrong,
                    modifier = Modifier.padding(top = Blesk.dimens.sectionGap, bottom = 6.dp).semantics { heading() },
                )
                line.startsWith("- ") -> Text(
                    text = "— " + line.removePrefix("- ").replace("`", ""),
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                else -> Text(line.replace("`", ""), color = Blesk.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

private const val PERCENT = 100

/** States in which a downloaded file was refused and deleted. */
private val REFUSED = setOf(UpdateProgress.Forged, UpdateProgress.Mismatch)
