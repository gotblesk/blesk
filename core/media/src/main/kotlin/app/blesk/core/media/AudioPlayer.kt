package app.blesk.core.media

import android.content.Context
import androidx.media3.common.MediaItem
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

/** What the single voice-note player is doing right now. */
data class Playback(val path: String? = null, val playing: Boolean = false, val positionMillis: Long = 0, val speed: Float = 1f)

/** One player for every voice note: starting one stops the other. */
class AudioPlayer(context: Context, private val scope: CoroutineScope) {
    private val player = ExoPlayer.Builder(context).build()
    private val state = MutableStateFlow(Playback())
    private var ticker: Job? = null

    val playback: StateFlow<Playback> = state.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.value = state.value.copy(playing = isPlaying)
                if (isPlaying) tick() else ticker?.cancel()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    player.pause()
                    player.seekTo(0)
                    state.value = state.value.copy(playing = false, positionMillis = 0)
                }
            }
        })
    }

    fun toggle(path: String) {
        if (state.value.path == path) {
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        player.setMediaItem(MediaItem.fromUri(path))
        player.playbackParameters = PlaybackParameters(state.value.speed)
        player.prepare()
        player.play()
        state.value = state.value.copy(path = path, positionMillis = 0)
    }

    fun seek(path: String, fraction: Float, durationMillis: Long) {
        if (state.value.path != path) toggle(path)
        player.seekTo((durationMillis * fraction.coerceIn(0f, 1f)).toLong())
    }

    /** Cycles 1x, 1.5x, 2x. */
    fun cycleSpeed() {
        val next = when (state.value.speed) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        player.playbackParameters = PlaybackParameters(next)
        state.value = state.value.copy(speed = next)
    }

    fun stop() {
        player.stop()
        state.value = Playback(speed = state.value.speed)
    }

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                state.value = state.value.copy(positionMillis = player.currentPosition)
                delay(TICK_MILLIS)
            }
        }
    }

    private companion object {
        const val TICK_MILLIS = 60L
    }
}
