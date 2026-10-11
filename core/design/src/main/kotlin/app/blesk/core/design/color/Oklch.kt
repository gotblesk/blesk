package app.blesk.core.design.color

import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * A colour in OKLCH: perceptual lightness [l] in 0..1, chroma [c] (0 is grey, ~0.37 is the
 * most saturated sRGB colour) and hue [h] in degrees. All palette maths happens here so that
 * changing a hue or a lightness changes how the colour is perceived and nothing else.
 */
data class Oklch(val l: Float, val c: Float, val h: Float) {

    fun withL(l: Float) = copy(l = l.coerceIn(0f, 1f))

    fun withC(c: Float) = copy(c = c.coerceAtLeast(0f))

    /** Converts to sRGB, pulling chroma in until the colour fits the gamut instead of clipping channels. */
    fun toColor(alpha: Float = 1f): Color {
        if (inGamut(l, c, h)) return toColorUnchecked(l, c, h, alpha)
        var low = 0f
        var high = c
        repeat(GAMUT_SEARCH_STEPS) {
            val mid = (low + high) / 2f
            if (inGamut(l, mid, h)) low = mid else high = mid
        }
        return toColorUnchecked(l, low, h, alpha)
    }

    companion object {
        private const val GAMUT_SEARCH_STEPS = 20
        private const val GAMUT_EPSILON = 1e-4f

        fun from(color: Color): Oklch {
            val r = color.red.toLinear()
            val g = color.green.toLinear()
            val b = color.blue.toLinear()
            val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
            val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
            val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
            val lightness = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
            val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
            val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
            val hue = Math.toDegrees(atan2(bb, a).toDouble()).toFloat().let { if (it < 0) it + 360f else it }
            return Oklch(lightness, hypot(a, bb), hue)
        }

        private fun linearRgb(l: Float, c: Float, h: Float): FloatArray {
            val rad = Math.toRadians(h.toDouble())
            val a = (c * cos(rad)).toFloat()
            val b = (c * sin(rad)).toFloat()
            val l3 = (l + 0.3963377774f * a + 0.2158037573f * b).pow(3)
            val m3 = (l - 0.1055613458f * a - 0.0638541728f * b).pow(3)
            val s3 = (l - 0.0894841775f * a - 1.2914855480f * b).pow(3)
            return floatArrayOf(
                4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3,
                -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3,
                -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3,
            )
        }

        private fun inGamut(l: Float, c: Float, h: Float): Boolean =
            linearRgb(l, c, h).all { it >= -GAMUT_EPSILON && it <= 1f + GAMUT_EPSILON }

        private fun toColorUnchecked(l: Float, c: Float, h: Float, alpha: Float): Color {
            val (r, g, b) = linearRgb(l, c, h)
            return Color(r.toGamma(), g.toGamma(), b.toGamma(), alpha.coerceIn(0f, 1f))
        }
    }
}

internal fun Float.toLinear(): Float =
    if (this <= 0.04045f) this / 12.92f else ((this + 0.055f) / 1.055f).pow(2.4f)

internal fun Float.toGamma(): Float {
    val v = coerceIn(0f, 1f)
    return if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
}

fun oklch(l: Float, c: Float, h: Float, alpha: Float = 1f): Color = Oklch(l, c, h).toColor(alpha)
