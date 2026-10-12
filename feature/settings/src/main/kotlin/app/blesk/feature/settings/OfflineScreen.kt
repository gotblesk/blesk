package app.blesk.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList

/** One kept file: what it is, from which chat, how big; [progress] while it is still coming. */
data class OfflineItem(
    val conversationId: String,
    val messageId: String,
    val title: String,
    val chat: String,
    val bytes: Long,
    val progress: Float? = null,
)

/** «Сохранённое офлайн»: files kept for watching without the internet, which clean-up leaves alone. */
@Composable
fun OfflineScreen(
    items: ImmutableList<OfflineItem>,
    onOpen: (OfflineItem) -> Unit,
    onRemove: (OfflineItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    ScrollScreen(title = stringResource(R.string.offline_title), onBack = onBack, modifier = modifier) {
        if (items.isEmpty()) {
            Text(stringResource(R.string.offline_empty), color = Blesk.colors.textSecondary, modifier = pad.padding(vertical = 8.dp))
            return@ScrollScreen
        }
        Card(pad) {
            items.forEach { item ->
                val size = Formatter.formatShortFileSize(context, item.bytes)
                val state = item.progress?.let { stringResource(R.string.offline_downloading, (it * PERCENT).toInt()) } ?: size
                val remove = stringResource(R.string.offline_remove, item.title)
                ListRow(
                    title = item.title,
                    subtitle = "${item.chat} · $state",
                    showChevron = false,
                    onClick = { onOpen(item) },
                    trailing = { IconButton(BleskIcons.Trash, remove, { onRemove(item) }) },
                )
            }
        }
        Text(
            stringResource(R.string.offline_hint),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = pad.padding(vertical = Blesk.dimens.sectionGap),
        )
    }
}

private const val PERCENT = 100
