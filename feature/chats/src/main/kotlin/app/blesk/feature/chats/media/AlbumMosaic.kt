package app.blesk.feature.chats.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Message
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import coil3.compose.AsyncImage
import java.io.File

/**
 * Photos and videos sent together, in rows that fill the width exactly: every row is as tall as
 * its pictures need to share the width at their own proportions, so there are no empty spaces.
 */
@Composable
internal fun AlbumMosaic(album: Attachment.Album, message: Message, width: Dp = MosaicWidth, onOpen: (index: Int) -> Unit) {
    val items = album.items
    val rows = rowsFor(items.size)
    var start = 0
    Column(Modifier.width(width).clip(RoundedCornerShape(16.dp)), verticalArrangement = Arrangement.spacedBy(Gap)) {
        rows.forEach { count ->
            val row = items.subList(start, start + count)
            val first = start
            start += count
            val ratios = row.map(::ratioOf)
            val height = (width - Gap * (count - 1)) / ratios.sum()
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                row.forEachIndexed { offset, item ->
                    val index = first + offset
                    Tile(item, Modifier.size(width = height * ratios[offset], height = height), index, items.size, onOpen) {
                        transferOverlay(message, "${message.id}/$index", item)?.invoke(this)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(
    item: Attachment,
    modifier: Modifier,
    index: Int,
    total: Int,
    onOpen: (Int) -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val video = item as? Attachment.Video
    val path = video?.thumbnail ?: (item as? Attachment.Photo)?.path.orEmpty()
    val preview = video?.preview ?: (item as? Attachment.Photo)?.preview.orEmpty()
    val label = stringResource(
        if (video != null) R.string.media_album_video else R.string.media_album_photo,
        index + 1,
        total,
    )
    Box(
        modifier
            .background(Blesk.colors.surfaceStrong)
            .clickable(role = Role.Image, onClickLabel = label) { onOpen(index) }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (path.isNotEmpty()) {
            AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            BlurredPreview(preview, Modifier.fillMaxSize())
        }
        if (video != null) PlayBadge(Modifier.size(36.dp))
        overlay()
    }
}

/** How many pictures go in each row, top to bottom; the busier rows go last, as the eye ends there. */
internal fun rowsFor(count: Int): List<Int> = when (count) {
    0 -> emptyList()
    1 -> listOf(1)
    2 -> listOf(2)
    3 -> listOf(1, 2)
    4 -> listOf(2, 2)
    5 -> listOf(2, 3)
    6 -> listOf(3, 3)
    7 -> listOf(2, 2, 3)
    8 -> listOf(2, 3, 3)
    9 -> listOf(3, 3, 3)
    else -> listOf(2, 2, 3, 3) + rowsFor(count - 10)
}

private fun ratioOf(item: Attachment): Float {
    val (width, height) = when (item) {
        is Attachment.Photo -> item.width to item.height
        is Attachment.Video -> item.width to item.height
        else -> 1 to 1
    }
    if (width <= 0 || height <= 0) return 1f
    return (width.toFloat() / height).coerceIn(MIN_RATIO, MAX_RATIO)
}

private val MosaicWidth = 260.dp
private val Gap = 2.dp
private const val MIN_RATIO = 0.6f
private const val MAX_RATIO = 1.8f
