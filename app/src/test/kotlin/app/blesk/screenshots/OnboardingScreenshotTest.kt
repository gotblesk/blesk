package app.blesk.screenshots

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import app.blesk.core.design.component.UsernameStatus
import app.blesk.core.design.component.rememberUsernameInput
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.onboarding.AccessProblem
import app.blesk.feature.onboarding.InviteStepActions
import app.blesk.feature.onboarding.InviteStepScreen
import app.blesk.feature.onboarding.InviteStepState
import app.blesk.feature.onboarding.KnownServer
import app.blesk.feature.onboarding.ServerStepActions
import app.blesk.feature.onboarding.ServerStepScreen
import app.blesk.feature.onboarding.ServerStepState
import app.blesk.feature.onboarding.UsernameStepActions
import app.blesk.feature.onboarding.UsernameStepScreen
import app.blesk.feature.onboarding.UsernameStepState
import app.blesk.feature.onboarding.WelcomeActions
import app.blesk.feature.onboarding.WelcomeScreen
import app.blesk.feature.onboarding.WelcomeState
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import app.blesk.shared.protocol.UsernameProblem
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
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
    @get:Rule
    val compose = createComposeRule()

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
        captureFrozen("build/screenshots/${screen}_$name.png") { Themed(textScale, fontScale, motion, content) }
    }

    @Composable
    private fun Themed(
        textScale: Float = 1f,
        fontScale: Float = 1f,
        motion: BleskMotion = BleskMotion(reduced = true),
        content: @Composable () -> Unit,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            BleskTheme(appearance = Appearance(theme = theme, textScale = textScale), motion = motion) { content() }
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

    @Composable
    private fun ServerStep(state: ServerStepState) {
        ServerStepScreen(
            address = rememberTextFieldState("chat.example.ru"),
            state = state,
            actions = ServerStepActions(onDone = {}, onBack = {}),
            scanner = {},
            proxy = {},
        )
    }

    private val lastServer =
        KnownServer("chat.example.ru", "blesk://server?url=https://chat.example.ru&pin=sha256/AbC+dE/f=")

    @Test
    fun server() = capture("00b_server") { ServerStep(ServerStepState(last = lastServer, custom = true)) }

    @Test
    fun serverLarge() = capture("00b_server_large", textScale = 1.5f) { ServerStep(ServerStepState(last = lastServer, custom = true)) }

    @Test
    fun serverCertificate() = capture("00b_server_certificate") {
        ServerStep(ServerStepState(custom = true, problem = AccessProblem.CertificateMismatch))
    }

    @Composable
    private fun InviteStep(state: InviteStepState) {
        InviteStepScreen(rememberTextFieldState("k7m2p9xq4tzd"), state, InviteStepActions(onNext = {}, onBack = {}))
    }

    @Test
    fun invite() = capture("00c_invite") { InviteStep(InviteStepState()) }

    @Test
    fun inviteLarge() = capture("00c_invite_large", textScale = 1.5f) { InviteStep(InviteStepState(required = false)) }

    @Test
    fun inviteWait() = capture("00c_invite_wait") {
        InviteStep(InviteStepState(problem = AccessProblem.TooManyAttempts(waitMillis = 120_000)))
    }

    private val usernameActions = UsernameStepActions(onCreate = {}, onBack = {}, onTerms = {}, onPolicy = {})

    @Composable
    private fun UsernameStep(typed: String, status: UsernameStatus) {
        UsernameStepScreen(rememberUsernameInput(typed), status, UsernameStepState(), usernameActions)
    }

    private val taken = UsernameStatus.Taken("egor", persistentListOf("egor27", "egor.m", "egor_42"))

    @Test
    fun usernameFree() = capture("00d_username_free") { UsernameStep("egor.m", UsernameStatus.Free("egor.m")) }

    @Test
    fun usernameTaken() = capture("00d_username_taken") { UsernameStep("egor", taken) }

    @Test
    fun usernameLarge() = capture("00d_username_large", textScale = 1.5f) { UsernameStep("egor", taken) }

    @Test
    fun usernameProblem() = capture("00d_username_problem") {
        UsernameStep("egor_", UsernameStatus.Problem(UsernameProblem.EdgeSeparator))
    }

    /** Typed for real: only typing goes through the field's cleaning, which explains what it took out. */
    @Test
    fun usernameLatin() = compose.captureFrozen(
        "build/screenshots/00d_username_latin_$name.png",
        act = { onNodeWithContentDescription("Имя пользователя").performTextInput("егор") },
    ) {
        Themed { UsernameStep("", UsernameStatus.Empty) }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }
    }
}
