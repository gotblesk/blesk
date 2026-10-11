package app.blesk.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk

/** Bytes taken by each kind of data; null while still counting. */
data class StorageUse(val media: Long, val database: Long, val cache: Long)

@Composable
fun StorageScreen(use: StorageUse?, onClearCache: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun size(bytes: Long?): String =
        bytes?.let { Formatter.formatShortFileSize(context, it) } ?: context.getString(R.string.storage_counting)
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    ScrollScreen(title = stringResource(R.string.storage_title), onBack = onBack, modifier = modifier) {
        Card(pad) {
            ListRow(title = stringResource(R.string.storage_media), subtitle = size(use?.media), showChevron = false)
            ListRow(title = stringResource(R.string.storage_database), subtitle = size(use?.database), showChevron = false)
            ListRow(title = stringResource(R.string.storage_cache), subtitle = size(use?.cache), showChevron = false)
        }
        Button(
            text = stringResource(R.string.storage_clear_cache),
            kind = ButtonKind.Secondary,
            onClick = onClearCache,
            modifier = Modifier.padding(Blesk.dimens.gutter).fillMaxWidth(),
        )
        Text(stringResource(R.string.storage_hint), style = Blesk.type.label, color = Blesk.colors.textSecondary, modifier = pad)
    }
}
