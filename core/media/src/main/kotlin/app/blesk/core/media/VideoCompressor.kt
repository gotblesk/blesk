package app.blesk.core.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Re-encodes a picked video to H.264/AAC with the short side at most 720 px. */
@UnstableApi
class VideoCompressor(private val context: Context) {

    /** Re-encodes [input] to H.264 and AAC; unless [original], the short side is cut to 720. */
    suspend fun compress(input: Uri, output: File, original: Boolean = false): Boolean {
        val shortSide = withContext(Dispatchers.IO) { shortSideOf(input) } ?: return false
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            output.delete()
                            if (continuation.isActive) continuation.resume(false)
                        }
                    })
                    .build()
                val effects = if (!original && shortSide > MAX_SHORT_SIDE) {
                    Effects(emptyList(), listOf(Presentation.createForShortSide(MAX_SHORT_SIDE)))
                } else {
                    Effects.EMPTY
                }
                val item = EditedMediaItem.Builder(MediaItem.fromUri(input)).setEffects(effects).build()
                transformer.start(item, output.path)
                continuation.invokeOnCancellation {
                    transformer.cancel()
                    output.delete()
                }
            }
        }
    }

    private fun shortSideOf(uri: Uri): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            minOf(w, h)
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val MAX_SHORT_SIDE = 720
    }
}
