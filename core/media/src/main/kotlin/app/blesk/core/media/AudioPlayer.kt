package app.blesk.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Whether the queue starts over when it ends, and whether one song plays again and again. */
enum class Repeat { Off, All, One }

/** What the single audio player is doing right now: which file, how far, and what to call it. */
data class Playback(
    val path: String? = null,
    val playing: Boolean = false,
    val positionMillis: Long = 0,
    val speed: Float = 1f,
    val title: String = "",
    val artist: String = "",
    val durationMillis: Long = 0,
    /** What will play, in order, and which of it plays now. */
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val repeat: Repeat = Repeat.Off,
    val shuffle: Boolean = false,
) {
    val track: Track? get() = queue.getOrNull(index)
}

/**
 * Something to play: a voice note or a song, with the names the player and the system show.
 * [from] says where it came from («Аня Соколова»); [conversationId] and [messageId] point back to
 * the message, for sharing it and keeping it offline.
 */
data class Track(
    val path: String,
    val title: String,
    val artist: String = "",
    val durationMillis: Long = 0,
    val music: Boolean = false,
    val from: String = "",
    val conversationId: String? = null,
    val messageId: String? = null,
)

/**
 * One player for every voice note and song: starting one stops the other. Voice notes play on
 * into the ones after them, and playback goes on with the app in the background. A voice note
 * played over music pauses the music, which carries on where it was once the voice notes end;
 * calls pause it through audio focus, and it resumes after them.
 */
@OptIn(UnstableApi::class)
class AudioPlayer(context: Context, private val scope: CoroutineScope) {
    private val meter = LoudnessMeter()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val speedState = MutableStateFlow(prefs.getFloat(KEY_SPEED, 1f).takeIf { it in SPEEDS } ?: 1f)
    private val exo = ExoPlayer.Builder(context, meter.renderers(context))
        .setHandleAudioBecomingNoisy(true)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .build()
    private val state = MutableStateFlow(Playback(speed = speedState.value))
    private val tracks = HashMap<String, Track>()
    private var queue: List<Track> = emptyList()
    private var ticker: Job? = null

    /** Music put aside for a voice note, to come back to. */
    private var held: Held? = null

    /** For the media session that shows the player in the shade and on the lock screen. */
    val player: Player get() = exo

    val playback: StateFlow<Playback> = state.asStateFlow()

    /** How loud the playing sound is right now, 0 to 1, measured from the track itself. */
    val level: StateFlow<Float> = meter.level

    /**
     * The speed of voice notes and round videos: one for both, kept until it is changed. Songs
     * always play as recorded. The pitch stays where it was at any speed.
     */
    val voiceSpeed: StateFlow<Float> = speedState.asStateFlow()

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.value = state.value.copy(playing = isPlaying)
                if (isPlaying) tick() else ticker?.cancel()
                if (!isPlaying) meter.quiet()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val track = mediaItem?.mediaId?.let(tracks::get) ?: return
                state.value = state.value.copy(
                    path = track.path,
                    positionMillis = 0,
                    title = track.title,
                    artist = track.artist,
                    durationMillis = track.durationMillis,
                    index = exo.currentMediaItemIndex,
                )
                applySpeed()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                val resume = held
                if (resume != null) {
                    held = null
                    load(resume.queue, resume.index, resume.positionMillis, play = resume.playing)
                    return
                }
                exo.pause()
                exo.seekTo(0, 0)
                state.value = state.value.copy(playing = false, positionMillis = 0, index = 0)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                state.value = state.value.copy(shuffle = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                state.value = state.value.copy(repeat = repeatOf(repeatMode))
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
        val current = state.value
        held = when {
            // A voice note over music: the music waits and comes back afterwards.
            !track.music && current.track?.music == true ->
                held ?: Held(queue, exo.currentMediaItemIndex, exo.currentPosition, exo.isPlaying)
            !track.music -> held
            else -> null
        }
        load(listOf(track) + next, 0, 0, play = true)
    }

    /** Plays [tracks] from [start], shuffled if asked: «Играть всё» and «Перемешать». */
    fun playAll(tracks: List<Track>, start: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        held = null
        exo.shuffleModeEnabled = shuffle
        load(tracks, start.coerceIn(0, tracks.lastIndex), 0, play = true)
    }

    /** Jumps to the [index]-th track of the queue. */
    fun playAt(index: Int) {
        if (index !in queue.indices) return
        exo.seekTo(index, 0)
        exo.play()
    }

    fun next() {
        if (exo.hasNextMediaItem()) exo.seekToNextMediaItem()
    }

    /** Back to the start of the song, or to the one before when it has only just begun. */
    fun previous() = exo.seekToPrevious()

    fun cycleRepeat() {
        exo.repeatMode = when (state.value.repeat) {
            Repeat.Off -> Player.REPEAT_MODE_ALL
            Repeat.All -> Player.REPEAT_MODE_ONE
            Repeat.One -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        exo.shuffleModeEnabled = !exo.shuffleModeEnabled
    }

    fun seek(path: String, fraction: Float, durationMillis: Long) {
        if (state.value.path != path) toggle(path)
        exo.seekTo((durationMillis * fraction.coerceIn(0f, 1f)).toLong())
        state.value = state.value.copy(positionMillis = exo.currentPosition)
    }

    /** 1× → 1,5× → 2× → 1×; any other speed goes back to 1×. */
    fun cycleSpeed() = setSpeed(
        when (speedState.value) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        },
    )

    fun setSpeed(speed: Float) {
        speedState.value = speed
        prefs.edit().putFloat(KEY_SPEED, speed).apply()
        applySpeed()
    }

    private fun applySpeed() {
        val speed = if (state.value.track?.music == true) 1f else speedState.value
        exo.playbackParameters = PlaybackParameters(speed)
        state.value = state.value.copy(speed = speed)
    }

    fun stop() {
        held = null
        exo.stop()
        exo.clearMediaItems()
        queue = emptyList()
        meter.quiet()
        state.value = Playback(speed = speedState.value, repeat = state.value.repeat, shuffle = state.value.shuffle)
    }

    private fun load(list: List<Track>, index: Int, positionMillis: Long, play: Boolean) {
        queue = list
        tracks.clear()
        list.forEach { tracks[it.path] = it }
        exo.setMediaItems(list.map(::itemOf), index, positionMillis)
        exo.prepare()
        if (play) exo.play() else exo.pause()
        val track = list[index]
        state.value = state.value.copy(
            path = track.path,
            positionMillis = positionMillis,
            title = track.title,
            artist = track.artist,
            durationMillis = track.durationMillis,
            queue = list,
            index = index,
        )
        applySpeed()
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

    private class Held(val queue: List<Track>, val index: Int, val positionMillis: Long, val playing: Boolean)

    companion object {
        /** Every speed voice notes and round videos can play at. */
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

        private const val TICK_MILLIS = 60L
        private const val PREFS = "player"
        private const val KEY_SPEED = "voice_speed"

        private fun repeatOf(mode: Int) = when (mode) {
            Player.REPEAT_MODE_ALL -> Repeat.All
            Player.REPEAT_MODE_ONE -> Repeat.One
            else -> Repeat.Off
        }
    }
}

/**
 * The loudness of the decoded sound, a few times a second: the root mean square of the 16-bit
 * samples going to the speaker, eased so the light pulses rather than flickers. No microphone
 * is involved.
 */
@OptIn(UnstableApi::class)
class LoudnessMeter : TeeAudioProcessor.AudioBufferSink {
    private val mutable = MutableStateFlow(0f)
    val level: StateFlow<Float> = mutable.asStateFlow()
    private var encoding = C.ENCODING_INVALID
    private var lastMillis = 0L

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.encoding = encoding
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (encoding != C.ENCODING_PCM_16BIT) return
        val now = System.currentTimeMillis()
        if (now - lastMillis < STEP_MILLIS) return
        lastMillis = now
        val samples = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        if (!samples.hasRemaining()) return
        var sum = 0.0
        var count = 0
        while (samples.hasRemaining()) {
            val sample = samples.get() / Short.MAX_VALUE.toDouble()
            sum += sample * sample
            count++
        }
        val rms = sqrt(sum / count).toFloat()
        val target = (rms * GAIN).coerceIn(0f, 1f)
        mutable.value = mutable.value + (target - mutable.value) * EASE
    }

    fun quiet() {
        mutable.value = 0f
    }

    /** Renderers whose sound goes through this meter on its way out, so the light can pulse with it. */
    fun renderers(context: Context): RenderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(TeeAudioProcessor(this@LoudnessMeter)))
                .build()
    }

    private companion object {
        const val STEP_MILLIS = 50L
        const val GAIN = 2.5f
        const val EASE = 0.5f
    }
}
