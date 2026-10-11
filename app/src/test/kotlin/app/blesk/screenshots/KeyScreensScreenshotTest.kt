package app.blesk.screenshots

import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.diagnostics.CrashReport
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.LogEntry
import app.blesk.core.design.motion.BleskMotion
import app.blesk.core.design.theme.BleskTheme
import app.blesk.feature.chats.ChatListScreen
import app.blesk.feature.chats.ConversationScreen
import app.blesk.feature.debug.DebugScreen
import app.blesk.feature.settings.AboutScreen
import app.blesk.feature.settings.AppearanceScreen
import app.blesk.feature.settings.SettingsScreen
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.TimeZone

/**
 * Key screens in every theme. The PNGs land in app/build/screenshots and are attached to each
 * release; they are reviewed by eye against the stop list and for contrast.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class KeyScreensScreenshotTest(private val name: String) {

    private val theme = ThemeSpec.presets.first { it.preset.name.lowercase() == name }

    private val now = Instant.parse("2026-10-11T15:00:00Z")
    private val seed by lazy { DemoSeed.from(ApplicationProvider.getApplicationContext<android.content.Context>().resources, now) }

    private fun capture(screen: String, content: @Composable () -> Unit) {
        captureRoboImage("build/screenshots/${screen}_$name.png") {
            BleskTheme(appearance = Appearance(theme = theme), motion = BleskMotion(reduced = true)) {
                content()
            }
        }
    }

    @Test
    fun chatList() = capture("01_chats") {
        ChatListScreen(
            conversations = seed.conversations.toImmutableList(),
            demo = true,
            now = now,
            onOpenConversation = {},
            onOpenSettings = {},
        )
    }

    @Test
    fun conversation() = capture("02_conversation") {
        val conversation = seed.conversations.first { it.id == DemoSeed.GROUP }
        ConversationScreen(
            conversation = conversation,
            messages = seed.messages.filter { it.conversationId == conversation.id }.toImmutableList(),
            authorName = { id -> seed.contacts.firstOrNull { it.id == id }?.name },
            onSend = {},
            onBack = {},
        )
    }

    @Test
    fun settings() = capture("03_settings") {
        SettingsScreen(demo = true, debugUnlocked = true, onBack = {}, onOpenAppearance = {}, onOpenAbout = {}, onOpenDebug = {})
    }

    @Test
    fun appearance() = capture("04_appearance") {
        AppearanceScreen(current = theme, onSelectTheme = {}, onBack = {})
    }

    @Test
    fun about() = capture("05_about") {
        AboutScreen(versionName = "0.1.0", debugUnlocked = false, onUnlockDebug = {}, onBack = {})
    }

    @Test
    fun debug() = capture("06_debug") {
        DebugScreen(
            entries = persistentListOf(
                LogEntry(now.toEpochMilli(), EventCode.AppStarted, 1),
                LogEntry(now.toEpochMilli() + 1200, EventCode.ScreenOpened, 2),
                LogEntry(now.toEpochMilli() + 5400, EventCode.ThemeChanged, 3),
            ),
            crash = CrashReport(now.toEpochMilli(), "java.lang.IllegalStateException\n    at app.blesk.Example.run(Example.kt:12)"),
            appInfo = "blesk 0.1.0",
            onClearCrash = {},
            onClearJournal = {},
            onBack = {},
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }

        @JvmStatic
        @BeforeClass
        fun fixTimeZone() {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
        }
    }
}
