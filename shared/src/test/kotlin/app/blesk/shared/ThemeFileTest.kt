package app.blesk.shared

import app.blesk.shared.appearance.ThemeFile
import app.blesk.shared.appearance.ThemePreset
import app.blesk.shared.appearance.ThemeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeFileTest {

    @Test
    fun `a saved theme opens as a custom theme with the same colours`() {
        val decoded = ThemeFile.decode(ThemeFile(theme = ThemeSpec.Lime).encode())!!
        assertEquals(ThemePreset.Custom, decoded.theme.preset)
        assertEquals(ThemeSpec.Lime.lightHue, decoded.theme.lightHue)
    }

    @Test
    fun `garbage and future versions are rejected`() {
        assertNull(ThemeFile.decode("not json"))
        assertNull(ThemeFile.decode("""{"blesk_theme": 99, "theme": {}}"""))
        assertNull(ThemeFile.decode("x".repeat(10_000)))
    }

    @Test
    fun `out of range values are clamped`() {
        val raw = ThemeFile(theme = ThemeSpec.Dark.copy(backgroundChroma = 5f, lightLightness = -1f)).encode()
        val theme = ThemeFile.decode(raw)!!.theme
        assertEquals(ThemeSpec.MAX_BACKGROUND_CHROMA, theme.backgroundChroma)
        assertEquals(ThemeSpec.MIN_LIGHT_L, theme.lightLightness)
    }
}
