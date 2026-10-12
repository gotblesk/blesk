package app.blesk.feature.profile

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.theme.Blesk
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Framing a new avatar: the photo moves and zooms under a round window, and only what is inside
 * it is kept. The square around the circle is what is saved, so it stays round everywhere.
 */
@Composable
fun AvatarCrop(source: Bitmap, onDone: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val image = remember(source) { source.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var window by remember { mutableFloatStateOf(0f) }
    val scrim = Blesk.colors.background.copy(alpha = SCRIM)
    val ring = Blesk.colors.textPrimary
    val hint = stringResource(R.string.profile_crop_hint)

    // The smallest scale at which the photo still covers the window; zoom multiplies it.
    fun cover(side: Float) = side / minOf(source.width, source.height).toFloat()

    fun clampPan(next: Offset, side: Float): Offset {
        val scale = cover(side) * zoom
        val spareX = max(0f, (source.width * scale - side) / 2f)
        val spareY = max(0f, (source.height * scale - side) / 2f)
        return Offset(next.x.coerceIn(-spareX, spareX), next.y.coerceIn(-spareY, spareY))
    }

    LensScaffold(top = { TopLens(title = stringResource(R.string.profile_crop_title), onBack = onCancel) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .semantics { contentDescription = hint }
                    .pointerInput(source) {
                        detectTransformGestures { _, move, gesture, _ ->
                            zoom = (zoom * gesture).coerceIn(1f, MAX_ZOOM)
                            pan = clampPan(pan + move, window)
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val side = size.minDimension * WINDOW
                    window = side
                    val scale = cover(side) * zoom
                    val drawn = IntSize((source.width * scale).roundToInt(), (source.height * scale).roundToInt())
                    val topLeft = center + pan - Offset(drawn.width / 2f, drawn.height / 2f)
                    drawImage(image, dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()), dstSize = drawn)
                    val hole = Path().apply {
                        addOval(Rect(center - Offset(side / 2, side / 2), Size(side, side)))
                    }
                    clipPath(hole, ClipOp.Difference) { drawRect(scrim) }
                    drawCircle(ring, radius = side / 2, style = Stroke(RING.toPx()))
                }
            }
            Row(
                Modifier.padding(Blesk.dimens.gutter).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    text = stringResource(R.string.profile_crop_cancel),
                    onClick = onCancel,
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    text = stringResource(R.string.profile_crop_done),
                    onClick = { onDone(crop(source, cover(window) * zoom, pan, window)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Box(Modifier.weight(1f))
        }
    }
}

/** The square of [source] under the window of [side] pixels, at [scale] and moved by [pan]. */
private fun crop(source: Bitmap, scale: Float, pan: Offset, side: Float): Bitmap {
    val sideInSource = (side / scale).roundToInt().coerceAtMost(minOf(source.width, source.height))
    val centerX = source.width / 2f - pan.x / scale
    val centerY = source.height / 2f - pan.y / scale
    val left = (centerX - sideInSource / 2f).roundToInt().coerceIn(0, source.width - sideInSource)
    val top = (centerY - sideInSource / 2f).roundToInt().coerceIn(0, source.height - sideInSource)
    val square = Bitmap.createBitmap(source, left, top, sideInSource, sideInSource)
    return if (sideInSource > OUTPUT) Bitmap.createScaledBitmap(square, OUTPUT, OUTPUT, true) else square
}

private const val WINDOW = 0.86f
private const val MAX_ZOOM = 5f
private const val SCRIM = 0.7f
private const val OUTPUT = 1024
private val RING = 2.dp
