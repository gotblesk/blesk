package app.blesk.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import app.blesk.core.data.model.Attachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 * samples the loudness for the waveform, which is all that is kept besides the audio.
 */
class VoiceRecorder(private val context: Context, private val vault: MediaVault) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var sampler: Job? = null
    private val samples = ArrayList<Int>()
    private val level = MutableStateFlow(0f)
    private val elapsed = MutableStateFlow(0L)

    /** Current loudness, 0..1, for the recording indicator. */
    val loudness: StateFlow<Float> = level.asStateFlow()
    val elapsedMillis: StateFlow<Long> = elapsed.asStateFlow()

    fun start(scope: CoroutineScope): Boolean {
        if (recorder != null) return true
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
        samples.clear()
        startedAt = SystemClock.elapsedRealtime()
        sampler = scope.launch {
            while (isActive) {
                val amplitude = runCatching { created.maxAmplitude }.getOrDefault(0)
                samples += amplitude
                level.value = (amplitude / MAX_AMPLITUDE).coerceIn(0f, 1f)
                elapsed.value = SystemClock.elapsedRealtime() - startedAt
                delay(SAMPLE_MILLIS)
            }
        }
        return true
    }

    /** Stops and returns the note, or null if it was too short to be meant. */
    fun finish(): Attachment.Voice? {
        val output = file
        val duration = SystemClock.elapsedRealtime() - startedAt
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
        level.value = 0f
        elapsed.value = 0
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
