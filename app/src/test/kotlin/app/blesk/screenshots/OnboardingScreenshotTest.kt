package app.blesk.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import app.blesk.core.design.component.UsernameStatus
import app.blesk.core.design.component.rememberUsernameInput
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.chats.ChatSample
import app.blesk.feature.onboarding.AccessProblem
import app.blesk.feature.onboarding.CodeOfferScreen
import app.blesk.feature.onboarding.GlintStepScreen
import app.blesk.feature.onboarding.InviteStepActions
import app.blesk.feature.onboarding.InviteStepScreen
import app.blesk.feature.onboarding.InviteStepState
import app.blesk.feature.onboarding.KnownServer
import app.blesk.feature.onboarding.ProfileStepActions
import app.blesk.feature.onboarding.ProfileStepScreen
import app.blesk.feature.onboarding.ProfileStepState
import app.blesk.feature.onboarding.ServerStepActions
import app.blesk.feature.onboarding.ServerStepScreen
import app.blesk.feature.onboarding.ServerStepState
import app.blesk.feature.onboarding.ThemeStepScreen
import app.blesk.feature.onboarding.UsernameStepActions
import app.blesk.feature.onboarding.UsernameStepScreen
import app.blesk.feature.onboarding.UsernameStepState
import app.blesk.feature.onboarding.WelcomeActions
import app.blesk.feature.onboarding.WelcomeScreen
import app.blesk.feature.onboarding.WelcomeState
import app.blesk.feature.settings.ThemePicker
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

    private val profileActions = ProfileStepActions(onPickPhoto = {}, onRemovePhoto = {}, onNext = {}, onSkip = {})

    @Test
    fun profile() = capture("00e_profile") {
        ProfileStepScreen(rememberTextFieldState(), ProfileStepState(username = "egor"), profileActions)
    }

    @Test
    fun profilePhoto() = capture("00e_profile_photo") {
        ProfileStepScreen(rememberTextFieldState("Егор"), ProfileStepState("egor", photo = twoTones(), photoVersion = 1), profileActions)
    }

    @Test
    fun profileLarge() = capture("00e_profile_large", textScale = 1.5f) {
        ProfileStepScreen(rememberTextFieldState("ㅤ"), ProfileStepState(username = "egor", refused = true), profileActions)
    }

    @Test
    fun code() = capture("00f_code") { CodeOfferScreen(onEnable = {}, onLater = {}) }

    @Test
    fun codeLarge() = capture("00f_code_large", textScale = 1.5f) { CodeOfferScreen(onEnable = {}, onLater = {}) }

    @Composable
    private fun ThemeStep() {
        ThemeStepScreen(picker = { ThemePicker(theme, onSelect = {}) }, preview = { ChatSample() }, onNext = {})
    }

    @Test
    fun themeStep() = capture("00g_theme") { ThemeStep() }

    @Test
    fun themeStepLarge() = capture("00g_theme_large", textScale = 1.5f) { ThemeStep() }

    @Test
    fun glint() = capture("00h_glint") { GlintStepScreen(channel = true, subscribe = true, onSubscribeChange = {}, onDone = {}) }

    @Test
    fun glintNoChannel() = capture("00h_glint_no_channel") {
        GlintStepScreen(channel = false, subscribe = true, onSubscribeChange = {}, onDone = {})
    }

    @Test
    fun glintLarge() = capture("00h_glint_large", textScale = 1.5f) {
        GlintStepScreen(channel = true, subscribe = true, onSubscribeChange = {}, onDone = {})
    }

    /** A person's photo stood in for by two flat halves drawn in code. */
    private fun twoTones(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(PHOTO_SIDE, PHOTO_SIDE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(SKY)
        canvas.drawRect(0f, PHOTO_SIDE / 2f, PHOTO_SIDE.toFloat(), PHOTO_SIDE.toFloat(), Paint().apply { color = SAND })
        return bitmap.asImageBitmap()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }
    }
}

private const val PHOTO_SIDE = 256
private val SKY = Color.rgb(122, 140, 153)
private val SAND = Color.rgb(201, 162, 122)
