package app.blesk.feature.chats.stickers

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.blesk.core.data.model.StickerFormat
import app.blesk.core.data.stickers.StickerSet
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.StickerMaker
import app.blesk.feature.chats.R
import app.blesk.feature.chats.SectionChip
import app.blesk.feature.chats.media.StickerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** What to make a sticker from: a picture to frame, or an animation that goes in as it is. */
sealed interface StickerSource {
    class Picture(val bitmap: Bitmap) : StickerSource

    class Ready(val path: String, val format: StickerFormat) : StickerSource
}

/** The maker's answer: where the sticker goes and what it says; [crop] is in the picture's pixels. */
class MadeSticker(
    val crop: android.graphics.Rect?,
    val outline: Boolean,
    val emoji: String,
    val caption: String,
    /** Null for a new set. */
    val setId: String?,
)

/**
 * A new sticker: frame the picture in a square (zoom out to keep it whole on a transparent
 * background), choose the white outline, give it an emoji to be found by and, if wanted, a caption.
 * The result shows as it will be sent.
 */
@Composable
fun StickerMakerScreen(
    source: StickerSource,
    sets: List<StickerSet>,
    initialSetId: String?,
    onDone: (MadeSticker) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emoji = rememberTextFieldState()
    val caption = rememberTextFieldState()
    var outline by rememberSaveable { mutableStateOf(true) }
    var setId by rememberSaveable { mutableStateOf(initialSetId ?: sets.firstOrNull()?.id) }
    var crop by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val notify = rememberNotify()
    val needEmoji = stringResource(R.string.sticker_need_emoji)
    ScrollScreen(title = stringResource(R.string.sticker_new), onBack = onBack, modifier = modifier) {
        when (source) {
            is StickerSource.Picture -> {
                Framing(source.bitmap) { crop = it }
                Preview(source.bitmap, crop, outline)
                ToggleRow(
                    title = stringResource(R.string.sticker_outline),
                    subtitle = stringResource(R.string.sticker_outline_hint),
                    checked = outline,
                    onCheckedChange = { outline = it },
                )
            }
            is StickerSource.Ready -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                StickerView(source.path, source.format, Modifier.size(PreviewSize), plays = Int.MAX_VALUE)
            }
        }
        TextInput(label = stringResource(R.string.sticker_emoji), state = emoji, hint = stringResource(R.string.sticker_emoji_hint))
        TextInput(label = stringResource(R.string.sticker_caption), state = caption, hint = stringResource(R.string.sticker_caption_hint))
        SectionHeader(stringResource(R.string.sticker_into_set))
        FlowRow(
            Modifier.padding(horizontal = Blesk.dimens.gutter),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            sets.forEach { set -> SectionChip(set.title, set.id == setId) { setId = set.id } }
            SectionChip(stringResource(R.string.stickers_new), setId == null) { setId = null }
        }
        Button(
            text = stringResource(R.string.sticker_add),
            icon = BleskIcons.Check,
            onClick = {
                val picked = emoji.text.toString().trim()
                if (picked.isEmpty()) {
                    notify(needEmoji)
                } else {
                    onDone(MadeSticker(crop, outline, picked, caption.text.toString().trim(), setId))
                }
            },
            modifier = Modifier.padding(Blesk.dimens.gutter).padding(top = 8.dp).fillMaxWidth(),
        )
    }
}

/**
 * The picture under a square window, moved and zoomed with two fingers. It may be zoomed out
 * until it is whole inside the window, which leaves the rest transparent.
 */
@Composable
private fun Framing(source: Bitmap, onCrop: (android.graphics.Rect) -> Unit) {
    val image = remember(source) { source.asImageBitmap() }
    val longSide = max(source.width, source.height).toFloat()
    // 1 is the whole picture in the window; the start fills the window, as most pictures are cropped.
    val cover = longSide / minOf(source.width, source.height).coerceAtLeast(1)
    var zoom by remember(source) { mutableFloatStateOf(cover) }
    var pan by remember(source) { mutableStateOf(Offset.Zero) }
    var window by remember { mutableFloatStateOf(0f) }
    val scrim = Blesk.colors.background.copy(alpha = SCRIM)
    val edge = Blesk.colors.textPrimary
    val hint = stringResource(R.string.sticker_frame_hint)

    fun scale() = window / longSide * zoom

    fun clamp(next: Offset): Offset {
        val s = scale()
        val spareX = max(0f, (source.width * s - window) / 2f)
        val spareY = max(0f, (source.height * s - window) / 2f)
        return Offset(next.x.coerceIn(-spareX, spareX), next.y.coerceIn(-spareY, spareY))
    }

    LaunchedEffect(zoom, pan, window) {
        if (window > 0f) onCrop(cropOf(source, scale(), pan, window))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = hint }
            .pointerInput(source) {
                detectTransformGestures { _, move, gesture, _ ->
                    zoom = (zoom * gesture).coerceIn(1f, cover * MAX_ZOOM)
                    pan = clamp(pan + move)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            window = size.minDimension * WINDOW
            val s = scale()
            val drawn = IntSize((source.width * s).roundToInt(), (source.height * s).roundToInt())
            val topLeft = center + pan - Offset(drawn.width / 2f, drawn.height / 2f)
            drawImage(image, dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()), dstSize = drawn)
            val hole = Path().apply { addRect(Rect(center - Offset(window / 2, window / 2), Size(window, window))) }
            clipPath(hole, ClipOp.Difference) { drawRect(scrim) }
            drawRect(edge, topLeft = center - Offset(window / 2, window / 2), size = Size(window, window), style = Stroke(EDGE.toPx()))
        }
    }
}

/** The sticker as it will be, redrawn a moment after the framing or the outline changes. */
@Composable
private fun Preview(source: Bitmap, crop: android.graphics.Rect?, outline: Boolean) {
    var shown by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source, crop, outline) {
        val area = crop ?: return@LaunchedEffect
        delay(PREVIEW_DELAY_MILLIS)
        shown = withContext(Dispatchers.Default) { StickerMaker.render(source, area, outline) }
    }
    val description = stringResource(R.string.sticker_preview)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(PreviewSize)
                .clip(RoundedCornerShape(20.dp))
                .background(Blesk.colors.surfaceStrong)
                .semantics { contentDescription = description },
        ) {
            shown?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
    }
}

/** The part of [source] under the window, cut to the picture itself where the window reaches past it. */
private fun cropOf(source: Bitmap, scale: Float, pan: Offset, window: Float): android.graphics.Rect {
    val half = window / scale / 2f
    val centerX = source.width / 2f - pan.x / scale
    val centerY = source.height / 2f - pan.y / scale
    return android.graphics.Rect(
        (centerX - half).roundToInt().coerceIn(0, source.width - 1),
        (centerY - half).roundToInt().coerceIn(0, source.height - 1),
        (centerX + half).roundToInt().coerceIn(1, source.width),
        (centerY + half).roundToInt().coerceIn(1, source.height),
    )
}

private const val WINDOW = 0.86f
private const val MAX_ZOOM = 5f
private const val SCRIM = 0.7f
private const val PREVIEW_DELAY_MILLIS = 150L
private val EDGE = 2.dp
private val PreviewSize = 140.dp
