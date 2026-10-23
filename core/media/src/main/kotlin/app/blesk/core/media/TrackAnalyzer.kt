package app.blesk.core.media

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.nio.ByteOrder
import kotlin.math.abs

/** What a music file is, for people who care: «FLAC», 24 bit, 48 kHz, or «MP3» at 320 kbit/s. */
data class TrackFormat(val codec: String, val bits: Int?, val sampleRateHz: Int?, val bitrateKbps: Int?)

/** A song's real waveform ([bars], each 0 to 1), its format and its cover from the tags, if any. */
class TrackInfo(val bars: FloatArray, val format: TrackFormat?, val cover: ByteArray?)

/**
 * Reads a music file once: decodes it to find how loud each stretch is, notes its format and
 * takes the cover picture out of its tags. Slow for a long file, so it runs off the main thread
 * and the results are kept for the files played lately.
 */
object TrackAnalyzer {
    private val cache = object : LinkedHashMap<String, TrackInfo>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackInfo>?) = size > CACHE_SIZE
    }

    @Synchronized
    fun cached(path: String): TrackInfo? = cache[path]

    /** Keeps [info] as what [path] holds, so the player draws it at once instead of reading the file. */
    @Synchronized
    fun remember(path: String, info: TrackInfo) {
        cache[path] = info
    }

    fun analyze(path: String, bars: Int = BARS): TrackInfo {
        cached(path)?.let { return it }
        val cover = runCatching {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(path)
                    embeddedPicture
                } finally {
                    release()
                }
            }
        }.getOrNull()
        val (levels, format) = runCatching { decode(path, bars) }.getOrDefault(FloatArray(bars) to null)
        val info = TrackInfo(levels, format, cover)
        remember(path, info)
        return info
    }

    private fun decode(path: String, count: Int): Pair<FloatArray, TrackFormat?> {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        val trackIndex = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return FloatArray(count) to null
        extractor.selectTrack(trackIndex)
        val input = extractor.getTrackFormat(trackIndex)
        val mime = input.getString(MediaFormat.KEY_MIME).orEmpty()
        val durationUs = if (input.containsKey(MediaFormat.KEY_DURATION)) input.getLong(MediaFormat.KEY_DURATION) else 0L
        val peaks = FloatArray(count)
        var bits: Int? = input.intOrNull(KEY_BITS_PER_SAMPLE)
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(input, null, null, 0)
            codec.start()
            val drained = Drain(peaks, durationUs, bits)
            var inputDone = false
            while (!drained.done) {
                if (!inputDone) inputDone = feed(codec, extractor)
                drained.take(codec)
            }
            bits = drained.bits
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        val loudest = peaks.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val format = TrackFormat(
            codec = codecName(mime),
            bits = bits.takeIf { lossless(mime) },
            sampleRateHz = input.intOrNull(MediaFormat.KEY_SAMPLE_RATE),
            bitrateKbps = input.intOrNull(MediaFormat.KEY_BIT_RATE)?.div(BITS_PER_KILOBIT)?.takeIf { !lossless(mime) },
        )
        return FloatArray(count) { peaks[it] / loudest } to format
    }

    /** Hands the decoder the next piece of the file; true once the file has ended. */
    private fun feed(codec: MediaCodec, extractor: MediaExtractor): Boolean {
        val slot = codec.dequeueInputBuffer(TIMEOUT_US)
        if (slot < 0) return false
        val size = extractor.readSampleData(codec.getInputBuffer(slot)!!, 0)
        if (size < 0) {
            codec.queueInputBuffer(slot, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        codec.queueInputBuffer(slot, 0, size, extractor.sampleTime, 0)
        extractor.advance()
        return false
    }

    /** Takes decoded sound from the decoder and keeps the loudest moment of each stretch in [peaks]. */
    private class Drain(private val peaks: FloatArray, private val durationUs: Long, var bits: Int?) {
        private val info = MediaCodec.BufferInfo()
        private var encoding = AudioFormat.ENCODING_PCM_16BIT
        var done = false

        fun take(codec: MediaCodec) {
            val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                encoding = codec.outputFormat.intOrNull(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT
                if (bits == null) bits = bitsOf(encoding)
            }
            if (out < 0) return
            val buffer = codec.getOutputBuffer(out)
            if (buffer != null && info.size > 0 && durationUs > 0) {
                val bar = ((info.presentationTimeUs.toDouble() / durationUs) * peaks.size).toInt().coerceIn(0, peaks.size - 1)
                peaks[bar] = maxOf(peaks[bar], peakOf(buffer.apply { position(info.offset) }, info.size, encoding))
            }
            codec.releaseOutputBuffer(out, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
        }
    }

    private fun peakOf(buffer: java.nio.ByteBuffer, size: Int, encoding: Int): Float {
        val data = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        var peak = 0f
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = data.asFloatBuffer()
                val step = SAMPLE_STEP
                var i = 0
                while (i < size / Float.SIZE_BYTES) {
                    peak = maxOf(peak, abs(floats.get(i)))
                    i += step
                }
            }
            else -> {
                val shorts = data.asShortBuffer()
                var i = 0
                while (i < size / Short.SIZE_BYTES) {
                    peak = maxOf(peak, abs(shorts.get(i) / Short.MAX_VALUE.toFloat()))
                    i += SAMPLE_STEP
                }
            }
        }
        return peak
    }

    private fun codecName(mime: String) = when (mime) {
        MediaFormat.MIMETYPE_AUDIO_FLAC -> "FLAC"
        MediaFormat.MIMETYPE_AUDIO_MPEG -> "MP3"
        MediaFormat.MIMETYPE_AUDIO_AAC -> "AAC"
        MediaFormat.MIMETYPE_AUDIO_OPUS -> "Opus"
        MediaFormat.MIMETYPE_AUDIO_VORBIS -> "Vorbis"
        MediaFormat.MIMETYPE_AUDIO_RAW, "audio/x-wav", "audio/wav" -> "WAV"
        "audio/alac" -> "ALAC"
        else -> mime.substringAfter('/').uppercase()
    }

    private val Lossless =
        setOf(MediaFormat.MIMETYPE_AUDIO_FLAC, MediaFormat.MIMETYPE_AUDIO_RAW, "audio/alac", "audio/wav")

    private fun lossless(mime: String) = mime in Lossless

    private fun bitsOf(encoding: Int) = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 8
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> 16
    }

    private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private const val BARS = 72
    private const val CACHE_SIZE = 12
    private const val LOAD_FACTOR = 0.75f
    private const val TIMEOUT_US = 10_000L
    private const val SAMPLE_STEP = 16
    private const val BITS_PER_KILOBIT = 1000
    private const val KEY_BITS_PER_SAMPLE = "bits-per-sample"
}
