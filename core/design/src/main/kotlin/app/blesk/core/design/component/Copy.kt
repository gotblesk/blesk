package app.blesk.core.design.component

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import app.blesk.core.design.R
import kotlinx.coroutines.launch

/**
 * Copies text and says so. Android 13 and later show their own confirmation, so «Скопировано»
 * is shown only below that. [sensitive] text (tokens, keys) is marked so the system keeps it
 * out of clipboard previews.
 */
@Composable
fun rememberCopy(): (text: String, sensitive: Boolean) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val notify = rememberNotify()
    val copied = stringResource(R.string.design_copied)
    return { text, sensitive ->
        val clip = ClipData.newPlainText("blesk", text)
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        scope.launch { clipboard.setClipEntry(ClipEntry(clip)) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) notify(copied)
    }
}
