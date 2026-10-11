package app.blesk.core.media

import kotlin.math.max
import kotlin.math.sqrt

/** Turns raw amplitude samples into a fixed number of bars between 0 and 1. */
object Waveform {
    const val BARS = 48
    private const val MAX_AMPLITUDE = 32767f

    fun bars(samples: List<Int>, count: Int = BARS): List<Float> {
        if (samples.isEmpty()) return List(count) { 0f }
        val result = FloatArray(count)
        for (i in 0 until count) {
            val from = i * samples.size / count
            val to = max(from + 1, (i + 1) * samples.size / count).coerceAtMost(samples.size)
            var peak = 0
            for (j in from until to) peak = max(peak, samples[j])
            // A square root keeps quiet speech visible next to loud peaks.
            result[i] = sqrt((peak / MAX_AMPLITUDE).coerceIn(0f, 1f))
        }
        return result.toList()
    }

    fun encode(bars: List<Float>): String = bars.joinToString("") { ('a' + (it.coerceIn(0f, 1f) * 25).toInt()).toString() }

    fun decode(encoded: String): List<Float> = encoded.map { ((it - 'a').coerceIn(0, 25)) / 25f }
}
