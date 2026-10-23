package app.blesk.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.onboarding.AccessProblem
import app.blesk.feature.onboarding.WelcomeActions
import app.blesk.feature.onboarding.WelcomeScreen
import app.blesk.feature.onboarding.WelcomeState
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The way into an account: the welcome screen and every step up to the chats. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class OnboardingScreenshotTest(private val name: String) {
    private val theme = ThemeSpec.presets.first { it.preset.name.lowercase() == name }

    /**
     * [textScale] is blesk's own text size (at most 1.3); [fontScale] is the phone's, which can go
     * much further.
     */
    private fun capture(
        screen: String,
        textScale: Float = 1f,
        fontScale: Float = 1f,
        motion: BleskMotion = BleskMotion(reduced = true),
        content: @Composable () -> Unit,
    ) {
        captureFrozen("build/screenshots/${screen}_$name.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BleskTheme(appearance = Appearance(theme = theme, textScale = textScale), motion = motion) { content() }
            }
        }
    }

    private val welcomeActions =
        WelcomeActions(onCreate = {}, onHaveAccount = null, onDemo = {}, onOtherServer = {}, onUpdate = {})

    @Test
    fun welcome() = capture("00a_welcome", motion = BleskMotion(reduced = false, still = true)) {
        WelcomeScreen(WelcomeState(), welcomeActions)
    }

    @Test
    fun welcomeLarge() = capture("00a_welcome_large", textScale = 1.5f) { WelcomeScreen(WelcomeState(), welcomeActions) }

    @Test
    fun welcomeHuge() = capture("00a_welcome_huge", fontScale = 2f) { WelcomeScreen(WelcomeState(), welcomeActions) }

    @Test
    fun welcomeUnreachable() = capture("00a_welcome_unreachable") {
        WelcomeScreen(WelcomeState(problem = AccessProblem.Unreachable(otherServer = false)), welcomeActions)
    }

    @Test
    fun welcomeOtherServer() = capture("00a_welcome_other_server") {
        WelcomeScreen(WelcomeState(server = "chat.example.ru", problem = AccessProblem.AppTooOld), welcomeActions)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }
    }
}
