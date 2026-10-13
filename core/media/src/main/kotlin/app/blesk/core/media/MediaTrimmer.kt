package app.blesk.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** A recorded piece: a voice note recorded in several goes is several of these, one after another. */
data class Piece(val path: String, val durationMillis: Long)

/** The part of [path] to keep, from [startMillis] to [endMillis] of that file. */
data class Clip(val path: String, val startMillis: Long, val endMillis: Long)

/**
 * Cuts recordings before they are sent: the start and the end trimmed off, and a voice note
 * recorded in several goes joined into one. Done on the phone with Media3 Transformer; the
 * result is AAC (and H.264 for a circle) in MP4.
 */
@OptIn(UnstableApi::class)
class MediaTrimmer(private val context: Context, private val vault: MediaVault) {

    /** [pieces] one after another, kept from [fromMillis] to [toMillis] of the whole, as one sound file. */
    suspend fun joinAudio(pieces: List<Piece>, fromMillis: Long, toMillis: Long): File? {
        val items = clips(pieces, fromMillis, toMillis).map { clip -> EditedMediaItem.Builder(item(clip)).build() }
        if (items.isEmpty()) return null
        return export(Composition.Builder(EditedMediaItemSequence.withAudioFrom(items)).build(), audioOnly = true)
    }

    /** The round video at [path], kept from [fromMillis] to [toMillis]. */
    suspend fun clipVideo(path: String, fromMillis: Long, toMillis: Long): File? {
        val item = EditedMediaItem.Builder(item(Clip(path, fromMillis, toMillis))).build()
        return export(Composition.Builder(EditedMediaItemSequence.withAudioAndVideoFrom(listOf(item))).build(), audioOnly = false)
    }

    private fun item(clip: Clip) = MediaItem.Builder()
        .setUri(clip.path)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.startMillis)
                .setEndPositionMs(clip.endMillis)
                .build(),
        )
        .build()

    private suspend fun export(composition: Composition, audioOnly: Boolean): File? {
        val output = vault.newFile(if (audioOnly) "m4a" else "mp4")
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(context)
                    .setMuxerFactory(InAppMp4Muxer.Factory().setAttemptStreamableOutputEnabled(true))
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .apply { if (!audioOnly) setVideoMimeType(MimeTypes.VIDEO_H264) }
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(output)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            output.delete()
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                    .build()
                transformer.start(composition, output.path)
                continuation.invokeOnCancellation {
                    transformer.cancel()
                    output.delete()
                }
            }
        }
    }

    companion object {
        /**
         * Which parts of which pieces make up [fromMillis]..[toMillis] of the pieces played one after
         * another. Pieces wholly outside are left out; the first and last kept ones are cut.
         */
        fun clips(pieces: List<Piece>, fromMillis: Long, toMillis: Long): List<Clip> {
            val result = ArrayList<Clip>()
            var offset = 0L
            pieces.forEach { piece ->
                val start = (fromMillis - offset).coerceIn(0L, piece.durationMillis)
                val end = (toMillis - offset).coerceIn(0L, piece.durationMillis)
                if (end > start) result += Clip(piece.path, start, end)
                offset += piece.durationMillis
            }
            return result
        }
    }
}
