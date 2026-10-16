package app.blesk.feature.chats.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.blockTaps
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import kotlin.math.max
import kotlin.math.roundToInt

/** Which part of the picture becomes the cover, as fractions of its width and height. */
data class CoverSquare(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Picks a square out of a photo for a cover: the photo moves and zooms under a fixed square frame
 * and always fills it, so the cover never has empty edges. «Готово» hands over the square.
 */
@Composable
fun CoverCrop(picture: ImageBitmap, onDone: (CoverSquare) -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val description = stringResource(R.string.music_cover_crop_hint)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .blockTaps(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val side = width - 2 * with(LocalDensity.current) { Blesk.dimens.gutter.toPx() }
            val frame = Rect(Offset((width - side) / 2f, (height - side) / 2f), Size(side, side))
            val fit = max(side / picture.width, side / picture.height)
            var scale by remember(picture) { mutableFloatStateOf(fit) }
            var offset by remember(picture) {
                mutableStateOf(Offset(frame.center.x - picture.width * fit / 2f, frame.center.y - picture.height * fit / 2f))
            }
            fun clamp(at: Offset, zoom: Float): Offset {
                val w = picture.width * zoom
                val h = picture.height * zoom
                return Offset(at.x.coerceIn(frame.right - w, frame.left), at.y.coerceIn(frame.bottom - h, frame.top))
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = description }
                    .pointerInput(picture) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(fit, fit * MAX_ZOOM)
                            val ratio = next / scale
                            val moved = centroid - (centroid - offset) * ratio + pan
                            scale = next
                            offset = clamp(moved, next)
                        }
                    },
            ) {
                drawImage(
                    picture,
                    dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
                    dstSize = IntSize((picture.width * scale).roundToInt(), (picture.height * scale).roundToInt()),
                )
                // Everything outside the square is dimmed; the square itself is outlined in light.
                val hole = Path().apply { addRoundRect(RoundRect(frame, CornerRadius(CORNER.dp.toPx()))) }
                clipPath(hole, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = SCRIM)) }
                drawRoundRect(
                    color = Color.White.copy(alpha = RIM),
                    topLeft = frame.topLeft,
                    size = frame.size,
                    cornerRadius = CornerRadius(CORNER.dp.toPx()),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            Text(
                description,
                color = Color.White,
                style = Blesk.type.label,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(Blesk.dimens.gutter),
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(Blesk.dimens.gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    stringResource(R.string.music_cancel),
                    onClick = onCancel,
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    stringResource(R.string.music_done),
                    onClick = {
                        val w = picture.width * scale
                        val h = picture.height * scale
                        onDone(CoverSquare((frame.left - offset.x) / w, (frame.top - offset.y) / h, frame.width / w, frame.height / h))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val MAX_ZOOM = 5f
private const val SCRIM = 0.6f
private const val RIM = 0.8f
private const val CORNER = 28
