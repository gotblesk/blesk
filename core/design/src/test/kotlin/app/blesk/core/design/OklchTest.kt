package app.blesk.core.design

import androidx.compose.ui.graphics.Color
import app.blesk.core.design.color.Oklch
import app.blesk.core.design.color.contrastRatio
import app.blesk.core.design.color.ensureContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OklchTest {

    @Test
    fun `white and black map to the ends of the lightness axis`() {
        assertEquals(1f, Oklch.from(Color.White).l, 1e-3f)
        assertEquals(0f, Oklch.from(Color.Black).l, 1e-3f)
        assertEquals(0f, Oklch.from(Color.White).c, 1e-3f)
    }

    @Test
    fun `round trip keeps the colour`() {
        val colors =
            listOf(Color(0xFF3A6EA5), Color(0xFFE3B505), Color(0xFF7A5C99), Color(0xFF1D1F22), Color(0xFFF2EEE8))
        colors.forEach { color ->
            val back = Oklch.from(color).toColor()
            assertEquals(color.red, back.red, 2e-3f)
            assertEquals(color.green, back.green, 2e-3f)
            assertEquals(color.blue, back.blue, 2e-3f)
        }
    }

    @Test
    fun `out of gamut colours lose chroma instead of clipping`() {
        val color = Oklch(0.9f, 0.4f, 260f).toColor()
        listOf(color.red, color.green, color.blue).forEach { assertTrue(it in 0f..1f) }
        assertTrue(Oklch.from(color).h in 230f..290f)
    }

    @Test
    fun `contrast of black on white is 21`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
    }

    @Test
    fun `ensureContrast lifts a weak colour to the threshold`() {
        val background = Color(0xFF202225)
        val weak = Color(0xFF4A4D52)
        val fixed = ensureContrast(weak, background, 4.5f)
        assertTrue(contrastRatio(fixed, background) >= 4.5f)
        assertTrue(contrastRatio(fixed, background) < 5.5f)
    }
}
