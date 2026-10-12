package app.blesk.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Bytes taken by each kind of data; null while still counting. */
data class StorageUse(
    val photos: Long,
    val videos: Long,
    val voice: Long,
    val files: Long,
    val database: Long,
    val cache: Long,
    /** Chats by the space their media takes, largest first. */
    val chats: ImmutableList<ChatUse> = persistentListOf(),
)

data class ChatUse(val id: String, val title: String, val bytes: Long)

/** «Хранить медиа» choices in days; 0 keeps media for good. */
val KEEP_MEDIA_DAYS = persistentListOf(0, 365, 90, 30)

class StorageActions(
    val onKeepMedia: (days: Int) -> Unit,
    val onClearChat: (id: String) -> Unit,
    val onClearCache: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
fun StorageScreen(use: StorageUse?, keepMediaDays: Int, actions: StorageActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun size(bytes: Long?): String =
        bytes?.let { Formatter.formatShortFileSize(context, it) } ?: context.getString(R.string.storage_counting)
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    ScrollScreen(title = stringResource(R.string.storage_title), onBack = actions.onBack, modifier = modifier) {
        Card(pad) {
            ListRow(title = stringResource(R.string.storage_photos), subtitle = size(use?.photos), showChevron = false)
            ListRow(title = stringResource(R.string.storage_videos), subtitle = size(use?.videos), showChevron = false)
            ListRow(title = stringResource(R.string.storage_voice), subtitle = size(use?.voice), showChevron = false)
            ListRow(title = stringResource(R.string.storage_files), subtitle = size(use?.files), showChevron = false)
            ListRow(title = stringResource(R.string.storage_database), subtitle = size(use?.database), showChevron = false)
            ListRow(title = stringResource(R.string.storage_cache), subtitle = size(use?.cache), showChevron = false)
        }
        Button(
            text = stringResource(R.string.storage_clear_cache),
            kind = ButtonKind.Secondary,
            onClick = actions.onClearCache,
            modifier = Modifier.padding(Blesk.dimens.gutter).fillMaxWidth(),
        )

        SectionHeader(stringResource(R.string.storage_keep))
        Segmented(
            options = persistentListOf(
                stringResource(R.string.storage_keep_forever),
                stringResource(R.string.storage_keep_year),
                stringResource(R.string.storage_keep_quarter),
                stringResource(R.string.storage_keep_month),
            ),
            selected = KEEP_MEDIA_DAYS.indexOf(keepMediaDays).coerceAtLeast(0),
            onSelect = { actions.onKeepMedia(KEEP_MEDIA_DAYS[it]) },
            modifier = pad,
        )
        Text(
            stringResource(R.string.storage_keep_hint),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = pad.padding(top = 8.dp),
        )

        val chats = use?.chats.orEmpty()
        if (chats.isNotEmpty()) {
            SectionHeader(stringResource(R.string.storage_by_chat))
            var picked by rememberSaveable { mutableStateOf<String?>(null) }
            Card(pad) {
                chats.forEach { chat ->
                    ListRow(
                        title = chat.title,
                        subtitle = size(chat.bytes),
                        showChevron = false,
                        onClick = { picked = if (picked == chat.id) null else chat.id },
                    )
                    // A second, deliberate tap: removing files cannot be undone from here.
                    if (picked == chat.id) {
                        Button(
                            text = stringResource(R.string.storage_clear_chat),
                            kind = ButtonKind.Secondary,
                            onClick = {
                                picked = null
                                actions.onClearChat(chat.id)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.storage_hint),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = pad.padding(vertical = Blesk.dimens.sectionGap),
        )
    }
}
