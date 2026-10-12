package app.blesk.core.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the single audio player is doing right now: which file, how far, and what to call it. */
data class Playback(
    val path: String? = null,
    val playing: Boolean = false,
    val positionMillis: Long = 0,
    val speed: Float = 1f,
    val title: String = "",
    val artist: String = "",
    val durationMillis: Long = 0,
)

/** Something to play: a voice note or a song, with the names the player and the system show. */
data class Track(val path: String, val title: String, val artist: String = "", val durationMillis: Long = 0)

/**
 * One player for every voice note and song: starting one stops the other. Voice notes play on
 * into the ones after them, and playback goes on with the app in the background.
 */
class AudioPlayer(context: Context, private val scope: CoroutineScope) {
    private val exo = ExoPlayer.Builder(context).setHandleAudioBecomingNoisy(true).build()
    private val state = MutableStateFlow(Playback())
    private val tracks = HashMap<String, Track>()
    private var ticker: Job? = null

    /** For the media session that shows the player in the shade and on the lock screen. */
    val player: Player get() = exo

    val playback: StateFlow<Playback> = state.asStateFlow()

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.value = state.value.copy(playing = isPlaying)
                if (isPlaying) tick() else ticker?.cancel()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val track = mediaItem?.mediaId?.let(tracks::get) ?: return
                state.value = state.value.copy(
                    path = track.path,
                    positionMillis = 0,
                    title = track.title,
                    artist = track.artist,
                    durationMillis = track.durationMillis,
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    exo.pause()
                    exo.seekTo(0, 0)
                    state.value = state.value.copy(playing = false, positionMillis = 0)
                }
            }
        })
    }

    fun toggle(path: String) = toggle(Track(path, ""))

    /** Plays [track], or pauses and resumes it; [next] follows when it ends. */
    fun toggle(track: Track, next: List<Track> = emptyList()) {
        if (state.value.path == track.path) {
            if (exo.isPlaying) exo.pause() else exo.play()
            return
        }
        val queue = listOf(track) + next
        tracks.clear()
        queue.forEach { tracks[it.path] = it }
        exo.setMediaItems(queue.map(::itemOf))
        exo.playbackParameters = PlaybackParameters(state.value.speed)
        exo.prepare()
        exo.play()
        state.value = state.value.copy(
            path = track.path,
            positionMillis = 0,
            title = track.title,
            artist = track.artist,
            durationMillis = track.durationMillis,
        )
    }

    fun seek(path: String, fraction: Float, durationMillis: Long) {
        if (state.value.path != path) toggle(path)
        exo.seekTo((durationMillis * fraction.coerceIn(0f, 1f)).toLong())
    }

    /** Cycles 1x, 1.5x, 2x. */
    fun cycleSpeed() {
        val next = when (state.value.speed) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        exo.playbackParameters = PlaybackParameters(next)
        state.value = state.value.copy(speed = next)
    }

    fun stop() {
        exo.stop()
        exo.clearMediaItems()
        state.value = Playback(speed = state.value.speed)
    }

    private fun itemOf(track: Track) = MediaItem.Builder()
        .setUri(track.path)
        .setMediaId(track.path)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build())
        .build()

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                state.value = state.value.copy(positionMillis = exo.currentPosition)
                delay(TICK_MILLIS)
            }
        }
    }

    private companion object {
        const val TICK_MILLIS = 60L
    }
}
