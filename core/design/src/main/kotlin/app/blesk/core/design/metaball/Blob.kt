package app.blesk.core.design.metaball

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp

/** One drop of the metaball field: a rounded rectangle. [radius] is clamped to half the short side. */
@Immutable
data class Blob(val rect: Rect, val radius: Float) {
    val clampedRadius: Float get() = radius.coerceAtMost(minOf(rect.width, rect.height) / 2f).coerceAtLeast(0f)

    companion object {
        fun circle(centerX: Float, centerY: Float, r: Float) = Blob(Rect(centerX - r, centerY - r, centerX + r, centerY + r), r)
    }
}

fun lerp(start: Blob, stop: Blob, fraction: Float) = Blob(
    rect = Rect(
        lerp(start.rect.left, stop.rect.left, fraction),
        lerp(start.rect.top, stop.rect.top, fraction),
        lerp(start.rect.right, stop.rect.right, fraction),
        lerp(start.rect.bottom, stop.rect.bottom, fraction),
    ),
    radius = lerp(start.radius, stop.radius, fraction),
)
