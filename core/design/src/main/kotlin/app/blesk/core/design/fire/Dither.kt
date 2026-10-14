package app.blesk.core.design.fire

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * Smooth gradients on an 8-bit screen break into visible bands where they change slowly. A faint
 * tile of noise laid over them — a step or two of brightness either way, fixed, so it never
 * crawls — breaks the band edges up: the eye sees the gradient, not the steps.
 */
fun Modifier.dithered(): Modifier = drawWithContent {
    drawContent()
    drawRect(DitherBrush)
}

private val DitherBrush: ShaderBrush by lazy { ShaderBrush(ImageShader(noise(), TileMode.Repeated, TileMode.Repeated)) }

private fun noise(): ImageBitmap {
    // Fixed seed: the same grain on every frame and every run.
    val random = Random(SEED)
    val pixels = IntArray(TILE * TILE) {
        val light = random.nextBoolean()
        val alpha = random.nextInt(MAX_ALPHA + 1)
        (alpha shl 24) or (if (light) 0xFFFFFF else 0x000000)
    }
    return android.graphics.Bitmap.createBitmap(pixels, TILE, TILE, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

private const val TILE = 64
private const val SEED = 7
private const val MAX_ALPHA = 6
