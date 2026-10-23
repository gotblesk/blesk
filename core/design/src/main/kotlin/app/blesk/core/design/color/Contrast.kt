package app.blesk.core.design.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** WCAG 2.x contrast thresholds. */
object ContrastLevel {
    const val BODY_TEXT = 4.5f

    /** Body text at the enhanced level, AAA. */
    const val ENHANCED_TEXT = 7f
    const val LARGE_TEXT = 3f
    const val UI_COMPONENT = 3f
}

fun relativeLuminance(color: Color): Float =
    0.2126f * color.red.toLinear() + 0.7152f * color.green.toLinear() + 0.0722f * color.blue.toLinear()

/** WCAG contrast ratio, 1..21. A translucent [foreground] is first composited over [background]. */
fun contrastRatio(foreground: Color, background: Color): Float {
    val fg = relativeLuminance(foreground.compositeOver(background))
    val bg = relativeLuminance(background)
    val lighter = maxOf(fg, bg)
    val darker = minOf(fg, bg)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Returns [foreground] unchanged if it already reaches [minRatio] against [background];
 * otherwise moves its OKLCH lightness away from the background (keeping hue and chroma)
 * in the smallest step that does.
 */
fun ensureContrast(foreground: Color, background: Color, minRatio: Float = ContrastLevel.BODY_TEXT): Color {
    if (contrastRatio(foreground, background) >= minRatio) return foreground
    val fg = Oklch.from(foreground)
    val bgIsDark = relativeLuminance(background) < 0.18f
    val target = if (bgIsDark) 1f else 0f
    var low = fg.l
    var high = target
    var best = fg.withL(target).toColor(foreground.alpha)
    repeat(24) {
        val mid = (low + high) / 2f
        val candidate = fg.withL(mid).toColor(foreground.alpha)
        if (contrastRatio(candidate, background) >= minRatio) {
            best = candidate
            high = mid
        } else {
            low = mid
        }
    }
    return best
}
