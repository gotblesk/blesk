package app.blesk.screenshots

import androidx.compose.runtime.Composable
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.settings.TermsScreen
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The rules in «О приложении», the same text as `docs/TERMS.md`. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class TermsScreenshotTest(private val name: String) {
    private val theme = ThemeSpec.presets.first { it.preset.name.lowercase() == name }

    private fun capture(screen: String, textScale: Float = 1f, content: @Composable () -> Unit) {
        captureFrozen("build/screenshots/${screen}_$name.png") {
            BleskTheme(appearance = Appearance(theme = theme, textScale = textScale), motion = BleskMotion(reduced = true)) { content() }
        }
    }

    @Test
    fun terms() = capture("07j_terms") { TermsScreen(onBack = {}) }

    @Test
    fun termsLargeText() = capture("07j_terms_large", textScale = 1.5f) { TermsScreen(onBack = {}) }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }
    }
}
