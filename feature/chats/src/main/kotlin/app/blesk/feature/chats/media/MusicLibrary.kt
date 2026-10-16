package app.blesk.feature.chats.media

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Message
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.StateMessage
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Something the library can play: a song from a chat or one of your own. */
sealed interface LibraryEntry {
    val title: String
    val artist: String
}

/** A song in the library: the message it came in, the chat's name and whether it is kept offline. */
class LibrarySong(val message: Message, val music: Attachment.Music, val from: String, val kept: Boolean) : LibraryEntry {
    override val title: String get() = music.title.ifEmpty { music.name }
    override val artist: String get() = music.artist
}

/** A song you added from the phone, with its cover: your own, from the tags, or none. */
class OwnSong(
    val id: String,
    override val title: String,
    override val artist: String,
    val durationMillis: Long,
    val cover: String?,
    /** The cover is one you picked, so it can be taken off again. */
    val ownCover: Boolean = false,
) : LibraryEntry

class LibraryActions(
    /** Plays [songs] from the [start]-th, shuffled if asked. */
    val play: (songs: List<LibraryEntry>, start: Int, shuffle: Boolean) -> Unit,
    /** Takes a song out of «Скачанное»; it stays in its chat. */
    val forget: (LibrarySong) -> Unit,
    /** Your own music; null where it is not offered. */
    val own: OwnMusicActions? = null,
)

/** What can be done with your own music. */
class OwnMusicActions(
    /** «Добавить с телефона»: the system picker for one or more audio files. */
    val add: () -> Unit,
    val rename: (id: String, title: String, artist: String) -> Unit,
    /** Pick a photo and crop it square. */
    val pickCover: (id: String) -> Unit,
    val removeCover: (id: String) -> Unit,
    /** Into a chat, chosen next. */
    val send: (id: String) -> Unit,
    val remove: (id: String) -> Unit,
)

private enum class Sort { New, Name }

/**
 * Your own music, everything kept for listening offline and every song from the chats: «Моя
 * музыка» first, then «Скачанное», then «Из чатов». Search by title or artist, newest first or by
 * name, «Играть всё» and «Перемешать». A downloaded song swiped aside leaves «Скачанное»; a long
 * press on your own song renames it, changes its cover, sends it to a chat or deletes it.
 */
@Composable
fun MusicLibrary(
    songs: ImmutableList<LibrarySong>,
    playingPath: String?,
    actions: LibraryActions,
    contentPadding: PaddingValues = PaddingValues(),
    own: ImmutableList<OwnSong> = persistentListOf(),
    playingOwn: String? = null,
) {
    val query = rememberTextFieldState()
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<OwnSong?>(null) }
    val needle = query.text.toString().trim()
    fun <T : LibraryEntry> List<T>.shown() = filter {
        needle.isEmpty() || it.title.contains(needle, true) || it.artist.contains(needle, true)
    }.let { list -> if (Sort.entries[sort] == Sort.Name) list.sortedBy { it.title.lowercase() } else list }
    val mine = own.shown()
    val shown = songs.shown()
    val kept = shown.filter { it.kept }
    val rest = shown.filterNot { it.kept }
    val ordered: List<LibraryEntry> = mine + kept + rest
    fun play(entry: LibraryEntry) = actions.play(ordered, ordered.indexOf(entry), false)
    LazyColumn(contentPadding = contentPadding) {
        item(key = "search") {
            TextInput(label = stringResource(R.string.library_search), state = query)
            Segmented(
                options = persistentListOf(stringResource(R.string.library_sort_new), stringResource(R.string.library_sort_name)),
                selected = sort,
                onSelect = { sort = it },
            )
        }
        actions.own?.let { ownActions ->
            item(key = "add") {
                Button(
                    text = stringResource(R.string.music_add),
                    icon = BleskIcons.Plus,
                    kind = ButtonKind.Secondary,
                    onClick = ownActions.add,
                    modifier = Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 4.dp),
                )
            }
        }
        if (songs.isEmpty() && own.isEmpty()) {
            item(key = "empty") { StateMessage(stringResource(R.string.library_empty)) }
            return@LazyColumn
        }
        if (ordered.isEmpty()) {
            item(key = "none") { StateMessage(stringResource(R.string.library_nothing_found)) }
            return@LazyColumn
        }
        item(key = "play") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    text = stringResource(R.string.library_play_all),
                    icon = BleskIcons.Play,
                    onClick = { actions.play(ordered, 0, false) },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    text = stringResource(R.string.library_shuffle),
                    icon = BleskIcons.Shuffle,
                    kind = ButtonKind.Secondary,
                    onClick = { actions.play(ordered, 0, true) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (mine.isNotEmpty()) {
            item(key = "mine") { SectionHeader(stringResource(R.string.music_mine)) }
            items(mine, key = { "own:" + it.id }) { song ->
                OwnSongRow(
                    song = song,
                    playing = song.id == playingOwn,
                    onPlay = { play(song) },
                    onMenu = actions.own?.let { { menu = song } },
                )
            }
        }
        if (kept.isNotEmpty()) {
            item(key = "kept") { SectionHeader(stringResource(R.string.library_downloaded)) }
            items(kept, key = { "kept:" + it.message.id }) { song ->
                SongRow(song, playing = song.music.path == playingPath, onPlay = { play(song) }, onForget = { actions.forget(song) })
            }
        }
        if (rest.isNotEmpty()) {
            item(key = "chats") { SectionHeader(stringResource(R.string.library_chats)) }
            items(rest, key = { "chat:" + it.message.id }) { song ->
                SongRow(song, playing = song.music.path == playingPath, onPlay = { play(song) }, onForget = null)
            }
        }
    }
    val ownActions = actions.own
    menu?.let { song -> if (ownActions != null) OwnSongSheet(song, ownActions, onClose = { menu = null }) }
}

@Composable
private fun SongRow(song: LibrarySong, playing: Boolean, onPlay: () -> Unit, onForget: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val offset = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { ForgetDistance.toPx() }
    val forgetLabel = stringResource(R.string.library_forget)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / (threshold * 2f)).coerceIn(0f, 0.6f)
            }
            .clickable(role = Role.Button, onClick = onPlay)
            .let { row ->
                if (onForget == null) return@let row
                row
                    .semantics { customActions = listOf(CustomAccessibilityAction(forgetLabel) { onForget(); true }) }
                    .pointerInput(song.message.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(offset.value) > threshold) {
                                    haptics.detach()
                                    onForget()
                                }
                                scope.launch { offset.animateTo(0f) }
                            },
                        ) { change, amount ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + amount) }
                        }
                    }
            }
            .padding(horizontal = Blesk.dimens.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon = if (playing) BleskIcons.Pause else BleskIcons.Music,
            contentDescription = null,
            tint = if (playing) Blesk.colors.textPrimary else Blesk.colors.textSecondary,
        )
        Column(Modifier.weight(1f)) {
            Text(song.music.title.ifEmpty { song.music.name }, style = Blesk.type.bodyStrong, maxLines = 1)
            val line = listOf(song.music.artist, song.from).filter { it.isNotEmpty() }.joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = Blesk.type.caption, color = Blesk.colors.textSecondary, maxLines = 1)
        }
        Text(clock(song.music.durationMillis), style = Blesk.type.caption, color = Blesk.colors.textSecondary)
        if (song.kept) Icon(BleskIcons.Download, contentDescription = null, tint = Blesk.colors.textTertiary, size = 18.dp)
    }
}

private val ForgetDistance = 96.dp
