package app.blesk.navigation

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import app.blesk.AppContainer
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Message
import app.blesk.core.data.settings.OfflineKey
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.motion.screenKey
import app.blesk.core.design.motion.sharedBounds
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.Track
import app.blesk.feature.chats.R
import app.blesk.feature.chats.media.LibraryActions
import app.blesk.feature.chats.media.LibrarySong
import app.blesk.feature.chats.media.MiniPlayer
import app.blesk.feature.chats.media.MiniPlayerActions
import app.blesk.feature.chats.media.MusicLibrary
import app.blesk.feature.chats.media.PlayerActions
import app.blesk.feature.chats.media.PlayerSheet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
    val music by remember(chats) { chats?.music ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val conversations by remember(chats) { chats?.conversations ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val kept by container.keptOffline.keys.collectAsStateWithLifecycle(initialValue = emptySet())
    val playback by container.audioPlayer.playback.collectAsStateWithLifecycle()
    val titles = remember(conversations) { conversations.associate { it.id to it.title } }
    val songs = remember(music, kept, titles) {
        music.mapNotNull { message ->
            val song = message.attachment as? Attachment.Music ?: return@mapNotNull null
            LibrarySong(message, song, titles[message.conversationId].orEmpty(), OfflineKey(message.conversationId, message.id) in kept)
        }.toImmutableList()
    }
    MusicLibrary(
        songs = songs,
        playingPath = playback.path.takeIf { playback.playing },
        actions = LibraryActions(
            play = { list, start, shuffle ->
                container.audioPlayer.playAll(list.map { trackOf(it.message, it.music, it.from) }, start, shuffle)
            },
            forget = { song ->
                scope.launch { container.keptOffline.set(OfflineKey(song.message.conversationId, song.message.id), false) }
            },
        ),
        contentPadding = padding,
    )
}

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
