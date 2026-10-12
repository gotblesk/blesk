package app.blesk.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Media kept in the app's private storage. Photos are decoded and re-encoded, which drops
 * EXIF (location, camera serial) on the way in; videos are re-encoded by [VideoCompressor].
 */
class MediaVault(private val context: Context) {
    private val root = File(context.filesDir, "media").apply { mkdirs() }
    private val resolver = context.contentResolver

    fun newFile(extension: String): File = File(root, "${UUID.randomUUID()}.$extension")

    fun owns(path: String): Boolean = File(path).canonicalPath.startsWith(root.canonicalPath)

    /** Deletes the files of an attachment that lives here; used when a message disappears. */
    fun forget(attachment: Attachment?) {
        attachment?.files.orEmpty().filter(::owns).forEach { File(it).delete() }
    }

    suspend fun importPhoto(uri: Uri): Attachment.Photo? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_PHOTO_SIDE) }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@withContext null
            val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            val bitmap = decoded.rotated(rotation).scaledDown(MAX_PHOTO_SIDE)
            val file = newFile("jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, it) }
            Attachment.Photo(file.path, bitmap.width, bitmap.height).also { bitmap.recycle() }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** Copies any file as is, up to [MAX_FILE_BYTES]. */
    suspend fun importFile(uri: Uri): Attachment.File? = withContext(Dispatchers.IO) {
        try {
            var name = "file"
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                    size = if (cursor.isNull(1)) -1 else cursor.getLong(1)
                }
            }
            if (size > MAX_FILE_BYTES) return@withContext null
            val safeName = name.replace(Regex("[/\\\\:*?\"<>|]"), "_").take(MAX_NAME)
            val extension = safeName.substringAfterLast('.', "bin").take(MAX_EXTENSION)
            val file = newFile(extension)
            val copied = resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            if (copied > MAX_FILE_BYTES) {
                file.delete()
                return@withContext null
            }
            Attachment.File(file.path, safeName, copied, resolver.getType(uri) ?: "application/octet-stream")
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** Reads size and length of a finished video and saves a poster frame next to it. */
    suspend fun describeVideo(file: File): Attachment.Video? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            var width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) width = height.also { height = width }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val frame = retriever.getFrameAtTime(0) ?: return@withContext null
            val poster = newFile("jpg")
            poster.outputStream().use { frame.scaledDown(MAX_POSTER_SIDE).compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, it) }
            Attachment.Video(file.path, poster.path, width, height, duration)
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    fun durationOf(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (_: RuntimeException) {
            0
        } finally {
            retriever.release()
        }
    }

    companion object {
        const val MAX_FILE_BYTES = 200L * 1024 * 1024
        private const val MAX_PHOTO_SIDE = 2560
        private const val MAX_POSTER_SIDE = 720
        private const val PHOTO_QUALITY = 86
        private const val MAX_NAME = 120
        private const val MAX_EXTENSION = 8

        internal fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= maxSide || height / (sample * 2) >= maxSide) sample *= 2
            return sample
        }
    }
}

private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    if (rotated !== this) recycle()
    return rotated
}

private fun Bitmap.scaledDown(maxSide: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return this
    val scale = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}
