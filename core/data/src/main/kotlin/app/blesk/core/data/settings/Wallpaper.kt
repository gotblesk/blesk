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
 * The chat wallpaper: the user's own photo, re-encoded and scaled down into private storage so
 * the original file and its metadata (location, camera) never stay with the app.
 */
class Wallpaper(context: Context) {
    private val resolver = context.contentResolver
    private val file = File(context.filesDir, "wallpaper.jpg")

    val exists: Boolean get() = file.exists()

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

    suspend fun load(): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) null else BitmapFactory.decodeFile(file.path)
    }

    fun clear() {
        file.delete()
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_SIDE || height / (sample * 2) >= MAX_SIDE) sample *= 2
        return sample
    }

    private companion object {
        const val MAX_SIDE = 1600
        const val QUALITY = 88
    }
}
