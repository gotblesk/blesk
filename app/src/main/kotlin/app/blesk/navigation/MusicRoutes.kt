package app.blesk.navigation

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import app.blesk.AppContainer
import app.blesk.Shared
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Message
import app.blesk.core.data.settings.OfflineKey
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.motion.screenKey
import app.blesk.core.design.motion.sharedBounds
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.Track
import app.blesk.feature.chats.R
import app.blesk.feature.chats.media.CoverCrop
import app.blesk.feature.chats.media.LibraryActions
import app.blesk.feature.chats.media.LibrarySong
import app.blesk.feature.chats.media.MiniPlayer
import app.blesk.feature.chats.media.MiniPlayerActions
import app.blesk.feature.chats.media.MusicLibrary
import app.blesk.feature.chats.media.OwnMusicActions
import app.blesk.feature.chats.media.OwnSong
import app.blesk.feature.chats.media.PlayerActions
import app.blesk.feature.chats.media.PlayerSheet
import app.blesk.music.OwnTrack
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object MusicKey : NavKey

/** The small player stuck to the bottom lens of whatever screen is open. */
@Composable
internal fun MusicCapsule(container: AppContainer, onExpand: () -> Unit) {
    val playback by container.audioPlayer.playback.collectAsStateWithLifecycle()
    val player = container.audioPlayer
    MiniPlayer(
        playback = playback,
        actions = MiniPlayerActions(
            onToggle = { playback.path?.let(player::toggle) },
            onStop = player::stop,
            onNext = player::next,
            onPrevious = player::previous,
            // Voice notes play without the big player; it is for music.
            onExpand = onExpand.takeIf { playback.track?.music == true },
        ),
    )
}

/** The whole player, over the current screen while [open]. */
@Composable
internal fun PlayerOverlay(
    container: AppContainer,
    open: Boolean,
    onClose: () -> Unit,
    onShare: (conversationId: String, messageId: String) -> Unit,
) {
    val playback by container.audioPlayer.playback.collectAsStateWithLifecycle()
    val kept by container.keptOffline.keys.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val motion = Blesk.motion
    val player = container.audioPlayer
    val track = playback.track
    val key = track?.let { t -> t.conversationId?.let { c -> t.messageId?.let { m -> OfflineKey(c, m) } } }
    AnimatedVisibility(
        visible = open && playback.path != null,
        enter = slideInVertically(motion.settle()) { it } + fadeIn(motion.press()),
        exit = slideOutVertically(motion.settle()) { it } + fadeOut(motion.press()),
    ) {
        val phone = stringResource(R.string.player_output_phone)
        PlayerSheet(
            playback = playback,
            level = { player.level.value },
            output = remember(playback.path) { outputName(context) ?: phone },
            actions = PlayerActions(
                toggle = { playback.path?.let(player::toggle) },
                next = player::next,
                previous = player::previous,
                seek = { fraction -> playback.path?.let { player.seek(it, fraction, playback.durationMillis) } },
                cycleRepeat = player::cycleRepeat,
                toggleShuffle = player::toggleShuffle,
                playAt = player::playAt,
                keepOffline = key?.let { k -> { on: Boolean -> scope.launch { container.keptOffline.set(k, on) } } },
                keptOffline = key != null && key in kept,
                share = key?.let { k -> { onShare(k.conversationId, k.messageId) } },
            ),
            library = { Library(container) },
            onClose = onClose,
        )
    }
}

/** «Ты → Музыка»: the library on a screen of its own. */
@Composable
internal fun MusicRoute(container: AppContainer, onBack: () -> Unit) {
    Box(Modifier.sharedBounds(screenKey("music"))) {
        LensScaffold(top = { TopLens(title = stringResource(R.string.library_title), onBack = onBack) }) { padding ->
            Library(container, padding)
        }
    }
}

@Composable
private fun Library(container: AppContainer, padding: PaddingValues = PaddingValues()) {
    val chats = container.chats.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notify = rememberNotify()
    val music by remember(chats) { chats?.music ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val conversations by remember(chats) { chats?.conversations ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val kept by container.keptOffline.keys.collectAsStateWithLifecycle(initialValue = emptySet())
    val playback by container.audioPlayer.playback.collectAsStateWithLifecycle()
    val ownTracks by container.ownMusic.tracks.collectAsStateWithLifecycle()
    val titles = remember(conversations) { conversations.associate { it.id to it.title } }
    val songs = remember(music, kept, titles) {
        music.mapNotNull { message ->
            val song = message.attachment as? Attachment.Music ?: return@mapNotNull null
            LibrarySong(message, song, titles[message.conversationId].orEmpty(), OfflineKey(message.conversationId, message.id) in kept)
        }.toImmutableList()
    }
    val own = remember(ownTracks) {
        ownTracks.sortedByDescending { it.addedAt }
            .map { OwnSong(it.id, it.title, it.artist, it.durationMillis, it.cover, ownCover = it.ownCover != null) }
            .toImmutableList()
    }
    val mine = stringResource(R.string.music_mine)
    val notAudio = stringResource(R.string.music_not_audio)
    val failed = stringResource(R.string.media_failed)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val skipped = container.ownMusic.add(uris, System.currentTimeMillis())
            val added = uris.size - skipped
            if (added > 0) notify(context.resources.getQuantityString(R.plurals.music_added, added, added))
            if (skipped > 0) notify(notAudio)
        }
    }
    var coverFor by remember { mutableStateOf<String?>(null) }
    var cropping by remember { mutableStateOf<Pair<Uri, ImageBitmap>?>(null) }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val preview = withContext(Dispatchers.IO) { previewOf(context, uri) }
            if (preview == null) notify(failed) else cropping = uri to preview
        }
    }
    Box(Modifier.fillMaxSize()) {
        MusicLibrary(
            songs = songs,
            own = own,
            playingPath = playback.path.takeIf { playback.playing },
            playingOwn = ownTracks.firstOrNull { it.path == playback.path && playback.playing }?.id,
            actions = LibraryActions(
                play = { list, start, shuffle ->
                    val tracks = list.mapNotNull { entry ->
                        when (entry) {
                            is LibrarySong -> trackOf(entry.message, entry.music, entry.from)
                            is OwnSong -> ownTracks.firstOrNull { it.id == entry.id }?.let { ownTrackOf(it, mine) }
                        }
                    }
                    container.audioPlayer.playAll(tracks, start.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)), shuffle)
                },
                forget = { song ->
                    scope.launch { container.keptOffline.set(OfflineKey(song.message.conversationId, song.message.id), false) }
                },
                own = OwnMusicActions(
                    add = { pick.launch(arrayOf("audio/*")) },
                    rename = { id, title, artist -> scope.launch { container.ownMusic.rename(id, title, artist) } },
                    pickCover = { id ->
                        coverFor = id
                        pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    removeCover = { id -> scope.launch { container.ownMusic.removeCover(id) } },
                    send = { id ->
                        val track = ownTracks.firstOrNull { it.id == id } ?: return@OwnMusicActions
                        scope.launch {
                            val attachment = container.ownMusic.forMessage(track)
                            if (attachment == null) {
                                notify(failed)
                            } else {
                                container.shareRequest.value = Shared(null, emptyList(), listOf(attachment))
                            }
                        }
                    },
                    remove = { id -> scope.launch { container.ownMusic.remove(id) } },
                ),
            ),
            contentPadding = padding,
        )
        cropping?.let { (uri, preview) ->
            CoverCrop(
                picture = preview,
                onDone = { square ->
                    val id = coverFor
                    cropping = null
                    if (id != null) {
                        scope.launch {
                            if (!container.ownMusic.setCover(id, uri, square, context.contentResolver)) notify(failed)
                        }
                    }
                },
                onCancel = { cropping = null },
            )
        }
    }
}

/** A downscaled copy of a picked photo to crop on screen; the cover itself is cut from the full one. */
private fun previewOf(context: Context, uri: Uri): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PREVIEW_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}.getOrNull()

private const val PREVIEW_SIDE = 1600

/** Your own song as the player knows it, with its cover. */
internal fun ownTrackOf(track: OwnTrack, from: String) = Track(
    path = track.path,
    title = track.title,
    artist = track.artist,
    durationMillis = track.durationMillis,
    music = true,
    from = from,
    cover = track.cover,
)

/** A song of a chat as the player knows it: where it came from, to share it and keep it offline. */
internal fun trackOf(message: Message, music: Attachment.Music, from: String) = Track(
    path = music.path,
    title = music.title.ifEmpty { music.name },
    artist = music.artist,
    durationMillis = music.durationMillis,
    music = true,
    from = from,
    conversationId = message.conversationId,
    messageId = message.id,
)

/** Where the sound goes when it is not the phone itself: Bluetooth or wired headphones, by name. */
private fun outputName(context: Context): String? {
    val audio = context.getSystemService(AudioManager::class.java) ?: return null
    val outside = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
    ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setOf(AudioDeviceInfo.TYPE_BLE_HEADSET) else emptySet()
    return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in outside }?.productName?.toString()
}
