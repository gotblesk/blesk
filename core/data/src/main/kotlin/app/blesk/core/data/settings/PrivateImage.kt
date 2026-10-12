package app.blesk.core.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * A picture the user chose (chat wallpaper, profile photo), re-encoded and scaled down into
 * private storage so the original file and its metadata (location, camera) never stay with
 * the app.
 */
class PrivateImage(context: Context, fileName: String, private val maxSide: Int) {
    private val resolver = context.contentResolver
    private val file = File(context.filesDir, fileName)

    val exists: Boolean get() = file.exists()

    /** The stored picture, for sending it on; null while there is none. */
    val stored: File? get() = file.takeIf { it.exists() }

    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext false
            val sample = sampleSize(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext false
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            bitmap.recycle()
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Decodes [uri] scaled down for editing, without keeping it; null if it cannot be read. */
    suspend fun decode(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** Keeps [bitmap] as this picture, for one that was cropped first. */
    suspend fun save(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            true
        } catch (_: IOException) {
            false
        }
    }

    suspend fun load(): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) null else BitmapFactory.decodeFile(file.path)
    }

    fun clear() {
        file.delete()
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxSide || height / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    private companion object {
        const val QUALITY = 88
    }
}
