package app.blesk.feature.chats.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.blesk.core.data.StreamSource
import app.blesk.core.data.model.Attachment
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.dragToDismiss
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.motion.sharedElement
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.EncryptedStream
import app.blesk.feature.chats.R
import coil3.compose.AsyncImage
import java.io.File
import app.blesk.core.design.R as DesignR

/**
 * Full-screen photos and videos of a chat, paged sideways from [start]: pinch to zoom a photo,
 * tap a video to pause it, swipe down to close. [onSave] is null where saving is turned off.
 */
@Composable
fun MediaViewer(
    items: List<Attachment>,
    start: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSave: ((Attachment) -> Unit)? = null,
    /** For a video still on its way: where to play it from while it downloads. */
    stream: suspend (page: Int) -> StreamSource? = { null },
    /** The [mediaKey] of each page, so the open picture grows out of its place in the chat and back. */
    sharedKeys: (page: Int) -> String? = { null },
) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize().dragToDismiss(onBack),
        ) { page ->
            // Only the page on screen travels back into the chat; its neighbours just fade.
            val sharedKey = if (pager.currentPage == page) sharedKeys(page) else null
            when (val item = items[page]) {
                is Attachment.Photo -> ZoomablePhoto(item, sharedKey)
                is Attachment.Video -> Box(Modifier.fillMaxSize()) {
                    Fitted(item.width, item.height, sharedKey) { Poster(item.thumbnail) }
                    // Only the video on screen plays; the neighbours wait as their posters.
                    when {
                        pager.currentPage != page -> Unit
                        item.path.isNotEmpty() -> VideoPlayer(item) { setMediaItem(MediaItem.fromUri(item.path)) }
                        else -> StreamingVideo(item) { stream(page) }
                    }
                }
                else -> Unit
            }
        }
        // In the corner window only the picture shows.
        if (LocalPictureInPicture.current?.active?.value == true) return@Box
        Row(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(BleskIcons.Back, stringResource(DesignR.string.design_back), onBack)
            if (items.size > 1) {
                Text(
                    text = stringResource(R.string.media_counter, pager.currentPage + 1, items.size),
                    style = Blesk.type.label,
                    color = Color.White,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else {
                Box(Modifier.weight(1f))
            }
            onSave?.let { save ->
                IconButton(BleskIcons.Download, stringResource(R.string.media_save), { items.getOrNull(pager.currentPage)?.let(save) })
            }
        }
    }
}

/**
 * The picture's own frame, centred: the shared element has the picture's shape, so it grows from
 * the cropped thumbnail without the black bars around it.
 */
@Composable
private fun Fitted(width: Int, height: Int, sharedKey: String?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val ratio = if (width > 0 && height > 0) width.toFloat() / height else 1f
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(ratio)
                .then(if (sharedKey != null) Modifier.sharedElement(sharedKey) else Modifier)
                .then(modifier),
            content = content,
        )
    }
}

@Composable
private fun Poster(path: String) {
    AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ZoomablePhoto(photo: Attachment.Photo, sharedKey: String?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    val zoomed = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationX = offset.x
        translationY = offset.y
    }
    Box(Modifier.fillMaxSize().transformable(state)) {
        Fitted(photo.width, photo.height, sharedKey, zoomed) {
            AsyncImage(
                model = File(photo.path),
                contentDescription = stringResource(R.string.media_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val MAX_ZOOM = 5f

/** A video that has not fully arrived: it plays as the ciphertext comes, decrypted on the fly. */
@OptIn(UnstableApi::class)
@Composable
private fun StreamingVideo(video: Attachment.Video, source: suspend () -> StreamSource?) {
    val stream by produceState<StreamSource?>(null, video) { value = source() }
    // Until then the poster underneath stays.
    val found = stream ?: return
    VideoPlayer(video) {
        val media = ProgressiveMediaSource.Factory(EncryptedStream.factory(found))
            .createMediaSource(MediaItem.fromUri(Uri.fromFile(found.ciphertext)))
        setMediaSource(media)
    }
}
