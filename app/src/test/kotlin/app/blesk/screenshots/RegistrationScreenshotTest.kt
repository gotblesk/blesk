package app.blesk.screenshots

import androidx.compose.runtime.Composable
import app.blesk.core.data.settings.ProxySetting
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.settings.ConnectOutcome
import app.blesk.feature.settings.InviteCreation
import app.blesk.feature.settings.InvitesScreen
import app.blesk.feature.settings.ProxyCheck
import app.blesk.feature.settings.ProxyControls
import app.blesk.feature.settings.ServerScreen
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import app.blesk.shared.protocol.InviteInfo
import app.blesk.shared.protocol.InviteList
import app.blesk.shared.protocol.Invites
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** Registration on blesk's own server by invite, and the invites every account hands out. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class RegistrationScreenshotTest(private val name: String) {
    private val theme = ThemeSpec.presets.first { it.preset.name.lowercase() == name }
    private val now = Frozen.moment

    private fun capture(screen: String, textScale: Float = 1f, content: @Composable () -> Unit) {
        captureFrozen("build/screenshots/${screen}_$name.png") {
            BleskTheme(appearance = Appearance(theme = theme, textScale = textScale), motion = BleskMotion(reduced = true)) { content() }
        }
    }

    private fun invite(code: String, hoursAgo: Long, usedBy: String? = null): InviteInfo {
        val created = now.minus(Duration.ofHours(hoursAgo)).toEpochMilli()
        val used = usedBy?.let { created + Duration.ofHours(5).toMillis() }
        return InviteInfo(code, created, created + Duration.ofDays(Invites.TTL_DAYS.toLong()).toMillis(), usedBy, used)
    }

    @Composable
    private fun RegisterForm() {
        ServerScreen(
            connected = null,
            onConnect = { ConnectOutcome.Ok },
            onBack = {},
            proxy = ProxyControls(ProxySetting(), {}, { ProxyCheck.Silent }),
            pendingInvite = "k7m2p9xq4tzd",
        )
    }

    @Test
    fun register() = capture("07g_server_register") { RegisterForm() }

    @Test
    fun registerLargeText() = capture("07g_server_register_large", textScale = 1.5f) { RegisterForm() }

    @Composable
    private fun SomeInvites() {
        InvitesScreen(
            invites = InviteList(
                invites = listOf(
                    invite("k7m2p9xq4tzd", hoursAgo = 3),
                    invite("h3n8c5vw2ry6", hoursAgo = 50),
                    invite("b4f9t2m7k3pa", hoursAgo = 80, usedBy = "vera.s"),
                ),
                left = 3,
            ),
            onCreate = { InviteCreation.Failed },
            onBack = {},
            opened = "k7m2p9xq4tzd",
        )
    }

    @Test
    fun invites() = capture("07h_invites") { SomeInvites() }

    @Test
    fun invitesLargeText() = capture("07h_invites_large", textScale = 1.5f) { SomeInvites() }

    @Test
    fun invitesAtLimit() = capture("07i_invites_limit") {
        val codes = listOf("q2w3e4r5t6y7", "a2s3d4f5g6h7", "z2x3c4v5b6n7", "m2n3b4v5c6x7", "p2r3s4t5u6v7")
        InvitesScreen(
            invites = InviteList(codes.mapIndexed { i, code -> invite(code, hoursAgo = 10L * i + 1) }, left = 0),
            onCreate = { InviteCreation.Limit },
            onBack = {},
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }
    }
}
