package app.blesk.feature.chats

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.MenuSheet
import app.blesk.core.design.icon.BleskIcons

/**
 * How picked photos and videos go out: «Сжатое» is smaller and quicker, «Оригинал» keeps the full
 * size. Either way the pictures are re-encoded, so place and camera never leave the phone.
 */
@Composable
fun QualitySheet(
    count: Int,
    originalBytes: Long,
    compressedBytes: Long,
    onSend: (original: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, originalBytes)
    val compressed = Formatter.formatShortFileSize(context, compressedBytes)
    MenuSheet(title = pluralStringResource(R.plurals.quality_title, count, count), onDismiss = onDismiss) {
        Card {
            ListRow(
                title = stringResource(R.string.quality_compressed),
                subtitle = stringResource(R.string.quality_compressed_hint, compressed),
                icon = BleskIcons.Image,
                showChevron = false,
                onClick = { onSend(false) },
            )
            ListRow(
                title = stringResource(R.string.quality_original),
                subtitle = stringResource(R.string.quality_original_hint, size),
                icon = BleskIcons.Download,
                showChevron = false,
                onClick = { onSend(true) },
            )
        }
    }
}
