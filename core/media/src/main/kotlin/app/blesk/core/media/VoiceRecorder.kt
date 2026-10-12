package app.blesk.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import app.blesk.core.data.model.Attachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Records voice notes: Opus in Ogg on Android 10+, AAC in MP4 before that. While recording it
 * samples the loudness for the waveform, which is all that is kept besides the audio. It runs in
 * its own scope, so a locked note goes on when the chat is closed or the app is in the background.
 */
class VoiceRecorder(private val context: Context, private val vault: MediaVault) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var recordedBefore = 0L
    private var resumedAt = 0L
    private var sampler: Job? = null
    private val pausedState = MutableStateFlow(false)
    private val activeState = MutableStateFlow(false)
    private val samples = ArrayList<Int>()
    private val level = MutableStateFlow(0f)
    private val elapsed = MutableStateFlow(0L)

    /** Current loudness, 0..1, for the recording indicator. */
    val loudness: StateFlow<Float> = level.asStateFlow()
    val elapsedMillis: StateFlow<Long> = elapsed.asStateFlow()
    val paused: StateFlow<Boolean> = pausedState.asStateFlow()

    /** From start until the note is sent or thrown away; the background service lives as long. */
    val active: StateFlow<Boolean> = activeState.asStateFlow()

    /** The chat the note being recorded is for; only that chat can send or drop it. */
    var owner: String? = null
        private set

    fun start(owner: String): Boolean {
        if (recorder != null) return this.owner == owner
        val opus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val output = vault.newFile(if (opus) "ogg" else "m4a")
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else legacyRecorder()
        try {
            created.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                if (opus) {
                    setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    setAudioEncodingBitRate(OPUS_BITRATE)
                } else {
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(AAC_BITRATE)
                }
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioChannels(1)
                setOutputFile(output.path)
                prepare()
                start()
            }
        } catch (_: IOException) {
            created.release()
            output.delete()
            return false
        } catch (_: RuntimeException) {
            created.release()
            output.delete()
            return false
        }
        recorder = created
        file = output
        this.owner = owner
        samples.clear()
        recordedBefore = 0L
        resumedAt = SystemClock.elapsedRealtime()
        pausedState.value = false
        activeState.value = true
        sampler = scope.launch {
            while (isActive) {
                // A pause leaves no flat stretch in the waveform: only what was recorded is drawn.
                if (!pausedState.value) {
                    val amplitude = runCatching { created.maxAmplitude }.getOrDefault(0)
                    samples += amplitude
                    level.value = (amplitude / MAX_AMPLITUDE).coerceIn(0f, 1f)
                    elapsed.value = recorded()
                }
                delay(SAMPLE_MILLIS)
            }
        }
        return true
    }

    fun pause() {
        val current = recorder ?: return
        if (pausedState.value) return
        if (runCatching { current.pause() }.isFailure) return
        recordedBefore = recorded()
        pausedState.value = true
        level.value = 0f
    }

    fun resume() {
        val current = recorder ?: return
        if (!pausedState.value) return
        if (runCatching { current.resume() }.isFailure) return
        resumedAt = SystemClock.elapsedRealtime()
        pausedState.value = false
    }

    private fun recorded(): Long =
        recordedBefore + if (pausedState.value) 0L else SystemClock.elapsedRealtime() - resumedAt

    /** Stops and returns the note, or null if it was too short to be meant. */
    fun finish(): Attachment.Voice? {
        val output = file
        val duration = recorded()
        stop()
        if (output == null) return null
        if (duration < MIN_DURATION_MILLIS) {
            output.delete()
            return null
        }
        return Attachment.Voice(output.path, duration, Waveform.encode(Waveform.bars(samples)))
    }

    fun cancel() {
        val output = file
        stop()
        output?.delete()
    }

    private fun stop() {
        sampler?.cancel()
        sampler = null
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        file = null
        owner = null
        level.value = 0f
        elapsed.value = 0
        pausedState.value = false
        activeState.value = false
    }

    @Suppress("DEPRECATION")
    private fun legacyRecorder() = MediaRecorder()

    private companion object {
        const val OPUS_BITRATE = 32_000
        const val AAC_BITRATE = 64_000
        const val SAMPLE_RATE = 48_000
        const val SAMPLE_MILLIS = 50L
        const val MAX_AMPLITUDE = 32767f
        const val MIN_DURATION_MILLIS = 700L
    }
}
