package app.blesk.core.design

import app.blesk.core.design.color.contrastRatio
import app.blesk.core.design.theme.BleskPalette
import app.blesk.shared.appearance.ThemePreset
import app.blesk.shared.appearance.ThemeSpec
import org.junit.Assert.assertTrue
import org.junit.Test

/** Text in every theme, including extreme custom ones, stays readable on every surface. */
class PaletteContrastTest {

    private val customThemes = buildList {
        for (l in listOf(0.12f, 0.3f, 0.5f, 0.62f, 0.8f, 0.97f)) {
            for (h in listOf(0f, 90f, 200f, 300f)) {
                add(ThemeSpec(ThemePreset.Custom, l, 0.12f, h, 0.95f, 0.2f, (h + 120f) % 360f, accentFromLight = h > 100f))
            }
        }
    }

    @Test
    fun `body text reaches WCAG AA in all presets and custom themes`() {
        (ThemeSpec.presets + customThemes).forEach { spec ->
            val c = BleskPalette.from(spec)
            listOf(c.background, c.surface, c.surfaceStrong).forEach { bg ->
                assertTrue("primary on $spec", contrastRatio(c.textPrimary, bg) >= 4.5f)
                assertTrue("secondary on $spec", contrastRatio(c.textSecondary, bg) >= 4.5f)
                assertTrue("tertiary on $spec", contrastRatio(c.textTertiary, bg) >= 3f)
                assertTrue("danger on $spec", contrastRatio(c.danger, bg) >= 4.5f)
            }
            assertTrue("accent on $spec", contrastRatio(c.accent, c.background) >= 3f)
            assertTrue("text on accent in $spec", contrastRatio(c.onAccent, c.accent) >= 4.5f)
        }
    }

    @Test
    fun `light theme background is not pure white so the glint shows`() {
        val c = BleskPalette.from(ThemeSpec.Light)
        assertTrue(contrastRatio(c.light, c.background) > 1.1f)
        assertTrue(contrastRatio(c.light, c.surface) > 1.03f)
    }
}
