package app.blesk.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import app.blesk.core.data.model.StickerFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A sticker file ready to go into a set. */
data class PreparedSticker(val file: File, val format: StickerFormat)

/**
 * Turns pictures into stickers: 512×512 on a transparent background, with an optional white
 * outline, as WebP within 512 KB. Animated WebP and Lottie files that already fit the limits go
 * in as they are, since cropping would lose the animation.
 */
class StickerMaker(private val context: Context) {
    private val resolver = context.contentResolver

    /** The picked picture upright and scaled down for cropping; null if it cannot be read. */
    suspend fun decode(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = MediaVault.sampleSize(bounds.outWidth, bounds.outHeight, EDIT_SIDE)
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@withContext null
            val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            decoded.rotated(rotation)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** An animated WebP or a Lottie file within the limits, copied as is; null for anything to be cropped instead. */
    suspend fun importAsIs(uri: Uri): PreparedSticker? = withContext(Dispatchers.IO) {
        try {
            val bytes = resolver.openInputStream(uri)?.use { it.readAtMost(MAX_BYTES + 1) } ?: return@withContext null
            val format = formatOf(bytes) ?: return@withContext null
            val file = File.createTempFile("sticker", ".${format.extension}", context.cacheDir)
            file.writeBytes(bytes)
            PreparedSticker(file, format)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** Lossless while it fits, then lossy, stepping the quality down until it is within 512 KB. */
    suspend fun encode(sticker: Bitmap): PreparedSticker? = withContext(Dispatchers.IO) {
        val lossless = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSLESS else legacyWebp()
        var bytes = compress(sticker, lossless, FULL_QUALITY)
        var quality = FIRST_LOSSY_QUALITY
        while (bytes.size > MAX_BYTES && quality >= MIN_QUALITY) {
            val lossy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else legacyWebp()
            bytes = compress(sticker, lossy, quality)
            quality -= QUALITY_STEP
        }
        if (bytes.size > MAX_BYTES) return@withContext null
        try {
            val file = File.createTempFile("sticker", ".webp", context.cacheDir)
            file.writeBytes(bytes)
            PreparedSticker(file, StickerFormat.Static)
        } catch (_: IOException) {
            null
        }
    }

    private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(format, quality, it) }.toByteArray()

    @Suppress("DEPRECATION")
    private fun legacyWebp() = Bitmap.CompressFormat.WEBP

    companion object {
        const val SIDE = 512
        const val MAX_BYTES = 512 * 1024
        const val MAX_LOTTIE_BYTES = 64 * 1024
        private const val EDIT_SIDE = 1536
        private const val OUTLINE_WIDTH = 10f
        private const val OUTLINE_STEPS = 24
        private const val FULL_QUALITY = 100
        private const val FIRST_LOSSY_QUALITY = 90
        private const val MIN_QUALITY = 40
        private const val QUALITY_STEP = 10
        private const val VP8X_FLAGS = 20
        private const val ANIMATION_FLAG = 0x02

        /**
         * Cuts [crop] (in [source]'s pixels) out of the picture into a 512×512 sticker. The outline is
         * the picture's own shape spread a few pixels in white, so a cut-out keeps its contour.
         */
        fun render(source: Bitmap, crop: Rect, outline: Boolean): Bitmap {
            val sticker = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
            val margin = if (outline) OUTLINE_WIDTH * 2f else 0f
            val scale = (SIDE - margin * 2) / max(crop.width(), crop.height()).coerceAtLeast(1)
            val width = crop.width() * scale
            val height = crop.height() * scale
            val target = RectF((SIDE - width) / 2f, (SIDE - height) / 2f, (SIDE + width) / 2f, (SIDE + height) / 2f)
            val picture = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
            Canvas(picture).drawBitmap(source, crop, target, Paint(Paint.FILTER_BITMAP_FLAG))
            val canvas = Canvas(sticker)
            if (outline) {
                val shape = picture.extractAlpha()
                val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                for (step in 0 until OUTLINE_STEPS) {
                    val angle = step * 2 * Math.PI / OUTLINE_STEPS
                    val dx = (cos(angle) * OUTLINE_WIDTH).toFloat()
                    val dy = (sin(angle) * OUTLINE_WIDTH).toFloat()
                    canvas.drawBitmap(shape, dx, dy, white)
                    canvas.drawBitmap(shape, dx / 2f, dy / 2f, white)
                }
                shape.recycle()
            }
            canvas.drawBitmap(picture, 0f, 0f, null)
            picture.recycle()
            return sticker
        }

        /** [formatOf] for a file, reading no more than the largest sticker allowed. */
        fun formatOf(file: File): StickerFormat? = try {
            formatOf(file.inputStream().use { it.readAtMost(MAX_BYTES + 1) })
        } catch (_: IOException) {
            null
        }

        /** Animated WebP or Lottie within its limit, judged by the bytes; null for everything else. */
        fun formatOf(bytes: ByteArray): StickerFormat? = when {
            bytes.isAnimatedWebp() -> StickerFormat.Animated.takeIf { bytes.size <= MAX_BYTES }
            bytes.isLottie() -> StickerFormat.Lottie.takeIf { bytes.size <= MAX_LOTTIE_BYTES }
            else -> null
        }

        private fun ByteArray.isAnimatedWebp(): Boolean =
            size > VP8X_FLAGS && ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" && ascii(12, 4) == "VP8X" &&
                (this[VP8X_FLAGS].toInt() and ANIMATION_FLAG) != 0

        private fun ByteArray.isLottie(): Boolean {
            if (isEmpty() || this[0] != '{'.code.toByte()) return false
            return try {
                val root = JSONObject(String(this, Charsets.UTF_8))
                root.has("v") && root.has("layers") && root.has("w") && root.has("h")
            } catch (_: JSONException) {
                false
            }
        }

        private fun ByteArray.ascii(from: Int, length: Int) = String(this, from, length, Charsets.US_ASCII)
    }
}

/** Up to [limit] bytes, enough to tell a file that is too big without reading all of it. */
private fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
