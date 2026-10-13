package app.blesk.feature.chats

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.visuals
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.StateMessage
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.motion.sharedElement
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.media.mediaKey
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The kinds of things a chat keeps, each its own tab on a profile or an info screen. */
enum class ArchiveKind(@param:StringRes val label: Int) {
    Media(R.string.person_tab_media),
    Files(R.string.person_tab_files),
    Links(R.string.person_tab_links),
    Voice(R.string.person_tab_voice),
}

/** Opening what the archive shows. */
class ArchiveActions(
    val onOpenMedia: (messageId: String) -> Unit,
    val onOpenFile: (Attachment.File) -> Unit,
    val onOpenLink: (String) -> Unit,
    val onPlayVoice: (path: String) -> Unit,
)

/**
 * One kind of what was sent in a chat, newest first: pictures in a grid, files, links and voice
 * messages in rows. [query] narrows it to what mentions those words.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Archive(kind: ArchiveKind, messages: ImmutableList<Message>, actions: ArchiveActions, query: String = "") {
    val newest = messages.asReversed().filter { query.isBlank() || matches(it, query) }
    val gutter = Modifier.padding(horizontal = Blesk.dimens.gutter)
    when (kind) {
        ArchiveKind.Media -> {
            val media = newest.filter { it.attachment?.visuals.orEmpty().isNotEmpty() }
            if (media.isEmpty()) return Empty()
            FlowRow(
                gutter.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                media.take(MAX_SHOWN).forEach { message ->
                    // An album shows its first picture here; the viewer pages through the rest.
                    val path = when (val a = message.attachment?.visuals?.firstOrNull()) {
                        is Attachment.Photo -> a.path
                        is Attachment.Video -> a.thumbnail
                        else -> return@forEach
                    }
                    AsyncImage(
                        model = File(path),
                        contentDescription = stringResource(R.string.person_open_media),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth(THIRD)
                            .aspectRatio(1f)
                            .sharedElement(mediaKey(message.id, 0))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Blesk.colors.surface)
                            .clickable(role = Role.Button) { actions.onOpenMedia(message.id) },
                    )
                }
            }
        }
        ArchiveKind.Files -> {
            val files = newest.mapNotNull { it.attachment as? Attachment.File }
            if (files.isEmpty()) return Empty()
            Card(gutter.padding(top = 8.dp)) {
                files.take(MAX_SHOWN).forEach { file ->
                    ListRow(
                        title = file.name,
                        subtitle = sizeLabel(file.sizeBytes),
                        icon = BleskIcons.Document,
                        showChevron = false,
                        onClick = { actions.onOpenFile(file) },
                    )
                }
            }
        }
        ArchiveKind.Links -> {
            val links = newest.flatMap { message -> LINK.findAll(message.text).map { it.value }.toList() }.distinct()
            if (links.isEmpty()) return Empty()
            Card(gutter.padding(top = 8.dp)) {
                links.take(MAX_SHOWN).forEach { link ->
                    ListRow(title = link, icon = BleskIcons.Forward, showChevron = false, onClick = { actions.onOpenLink(link) })
                }
            }
        }
        ArchiveKind.Voice -> {
            val voices = newest.filter { it.attachment is Attachment.Voice }
            if (voices.isEmpty()) return Empty()
            Card(gutter.padding(top = 8.dp)) {
                voices.take(MAX_SHOWN).forEach { message ->
                    val voice = message.attachment as Attachment.Voice
                    ListRow(
                        title = dateFormat.format(message.sentAt.atZone(ZoneId.systemDefault())),
                        subtitle = durationLabel(voice.durationMillis),
                        icon = BleskIcons.Play,
                        showChevron = false,
                        onClick = { actions.onPlayVoice(voice.path) },
                    )
                }
            }
        }
    }
}

private fun matches(message: Message, query: String): Boolean =
    message.text.contains(query, ignoreCase = true) ||
        (message.attachment as? Attachment.File)?.name?.contains(query, ignoreCase = true) == true

@Composable
private fun Empty() {
    StateMessage(stringResource(R.string.person_nothing_yet))
}

@Composable
private fun sizeLabel(bytes: Long): String = when {
    bytes >= MEGABYTE -> stringResource(R.string.person_size_mb, bytes / MEGABYTE.toFloat())
    else -> stringResource(R.string.person_size_kb, (bytes / KILOBYTE).coerceAtLeast(1))
}

private fun durationLabel(millis: Long): String {
    val seconds = millis / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private val LINK = Regex("https?://[^\\s]+")
private val dateFormat = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru"))
private const val MAX_SHOWN = 60
private const val THIRD = 0.32f
private const val MEGABYTE = 1_048_576L
private const val KILOBYTE = 1_024L
