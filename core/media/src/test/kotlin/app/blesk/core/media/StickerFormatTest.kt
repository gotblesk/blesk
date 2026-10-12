package app.blesk.core.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.model.StickerFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class StickerFormatTest {
    @Test
    fun animatedWebpIsKeptAsItIs() {
        assertEquals(StickerFormat.Animated, StickerMaker.formatOf(webp(animated = true)))
    }

    @Test
    fun stillWebpGoesThroughCropping() {
        assertNull(StickerMaker.formatOf(webp(animated = false)))
    }

    @Test
    fun lottieWithinLimitIsKept() {
        val lottie = """{"v":"5.7.0","w":512,"h":512,"layers":[]}""".toByteArray()
        assertEquals(StickerFormat.Lottie, StickerMaker.formatOf(lottie))
    }

    @Test
    fun lottieOverLimitIsRefused() {
        val padding = " ".repeat(StickerMaker.MAX_LOTTIE_BYTES)
        val lottie = """{"v":"5.7.0","w":512,"h":512,"layers":[],"nm":"$padding"}""".toByteArray()
        assertNull(StickerMaker.formatOf(lottie))
    }

    @Test
    fun otherJsonIsNotLottie() {
        assertNull(StickerMaker.formatOf("""{"name":"set"}""".toByteArray()))
    }

    private fun webp(animated: Boolean): ByteArray {
        val bytes = ByteArray(64)
        "RIFF".toByteArray().copyInto(bytes, 0)
        "WEBP".toByteArray().copyInto(bytes, 8)
        "VP8X".toByteArray().copyInto(bytes, 12)
        bytes[20] = if (animated) 0x02 else 0x00
        return bytes
    }
}
