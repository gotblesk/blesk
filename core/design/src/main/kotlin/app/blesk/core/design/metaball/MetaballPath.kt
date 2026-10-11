package app.blesk.core.design.metaball

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import kotlin.math.max
import kotlin.math.min

/**
 * The geometric fallback for devices without runtime shaders: the union of the rounded
 * rectangles plus a concave bridge between every pair of neighbours that the shader would
 * join, which happens when their gap is under half the smoothness.
 */
internal object MetaballPath {

    fun build(blobs: List<Blob>, smoothness: Float): Path {
        val result = Path()
        blobs.forEach { blob ->
            val shape = Path().apply {
                addRoundRect(RoundRect(blob.rect, CornerRadius(blob.clampedRadius)))
            }
            result.op(result, shape, PathOperation.Union)
        }
        for (i in blobs.indices) {
            for (j in i + 1 until blobs.size) {
                bridge(blobs[i], blobs[j], smoothness)?.let { result.op(result, it, PathOperation.Union) }
            }
        }
        return result
    }

    /** A neck between two drops stacked vertically or side by side, or null if they are too far apart. */
    private fun bridge(a: Blob, b: Blob, smoothness: Float): Path? {
        val reach = smoothness / 2f
        val upper = if (a.rect.center.y <= b.rect.center.y) a else b
        val lower = if (upper === a) b else a
        val verticalGap = lower.rect.top - upper.rect.bottom
        val left = max(upper.rect.left, lower.rect.left)
        val right = min(upper.rect.right, lower.rect.right)
        if (verticalGap < reach && right - left > 0f) {
            return verticalNeck(upper, lower, left, right, reach)
        }
        val first = if (a.rect.center.x <= b.rect.center.x) a else b
        val second = if (first === a) b else a
        val horizontalGap = second.rect.left - first.rect.right
        val top = max(first.rect.top, second.rect.top)
        val bottom = min(first.rect.bottom, second.rect.bottom)
        if (horizontalGap < reach && bottom - top > 0f) {
            return horizontalNeck(first, second, top, bottom, reach)
        }
        return null
    }

    private fun verticalNeck(upper: Blob, lower: Blob, left: Float, right: Float, reach: Float): Path {
        val gap = (lower.rect.top - upper.rect.bottom).coerceAtLeast(0f)
        // The closer the drops, the wider the neck, like surface tension pulling them together.
        val pull = 1f - gap / reach
        val inset = (right - left) / 2f * (1f - pull) + min(upper.clampedRadius, lower.clampedRadius) * (1f - pull)
        val l = (left + inset).coerceAtMost((left + right) / 2f)
        val r = (right - inset).coerceAtLeast((left + right) / 2f)
        val top = upper.rect.bottom - upper.clampedRadius
        val bottom = lower.rect.top + lower.clampedRadius
        val mid = (upper.rect.bottom + lower.rect.top) / 2f
        val waist = (r - l) * 0.25f * (1f - pull)
        return Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            quadraticTo(r - waist, mid, right, bottom)
            lineTo(left, bottom)
            quadraticTo(l + waist, mid, left, top)
            close()
        }
    }

    private fun horizontalNeck(first: Blob, second: Blob, top: Float, bottom: Float, reach: Float): Path {
        val gap = (second.rect.left - first.rect.right).coerceAtLeast(0f)
        val pull = 1f - gap / reach
        val inset = (bottom - top) / 2f * (1f - pull)
        val t = (top + inset).coerceAtMost((top + bottom) / 2f)
        val b = (bottom - inset).coerceAtLeast((top + bottom) / 2f)
        val start = first.rect.right - first.clampedRadius
        val end = second.rect.left + second.clampedRadius
        val mid = (first.rect.right + second.rect.left) / 2f
        val waist = (b - t) * 0.25f * (1f - pull)
        return Path().apply {
            moveTo(start, top)
            quadraticTo(mid, t + waist, end, top)
            lineTo(end, bottom)
            quadraticTo(mid, b - waist, start, bottom)
            close()
        }
    }
}
