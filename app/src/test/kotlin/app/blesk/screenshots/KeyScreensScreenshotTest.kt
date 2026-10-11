package app.blesk.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.diagnostics.CrashReport
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.LogEntry
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.design.component.DemoTag
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.LensTab
import app.blesk.core.design.glass.LensTabBar
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.motion.BleskMotion
import app.blesk.core.design.theme.BleskTheme
import app.blesk.feature.chats.ChatList
import app.blesk.feature.chats.ContactList
import app.blesk.feature.chats.ConversationCallbacks
import app.blesk.feature.chats.ConversationScreen
import app.blesk.feature.chats.VoiceChannelScreen
import app.blesk.feature.debug.DebugScreen
import app.blesk.feature.settings.AboutScreen
import app.blesk.feature.settings.AppearanceFileActions
import app.blesk.feature.settings.AppearanceScreen
import app.blesk.feature.settings.SettingsList
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
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
    private val tabs = persistentListOf(
        LensTab("Чаты", BleskIcons.Chats),
        LensTab("Контакты", BleskIcons.People),
        LensTab("Настройки", BleskIcons.Settings),
    )

    private fun capture(screen: String, content: @Composable () -> Unit) {
        captureRoboImage("build/screenshots/${screen}_$name.png") {
            BleskTheme(appearance = Appearance(theme = theme), motion = BleskMotion(reduced = true)) {
                content()
            }
        }
    }

    @Composable
    private fun HomeFrame(title: String, tab: Int, content: @Composable (PaddingValues) -> Unit) {
        LensScaffold(
            top = { TopLens(title = title, actions = { DemoTag() }) },
            bottom = { LensTabBar(tabs = tabs, selected = tab, onSelect = {}) },
            content = content,
        )
    }

    @Test
    fun chatList() = capture("01_chats") {
        HomeFrame("Чаты", 0) { padding ->
            ChatList(
                conversations = seed.conversations.filter { it.parentId == null }.toImmutableList(),
                typing = persistentSetOf(DemoSeed.MISHA),
                now = now,
                contentPadding = padding,
                onOpenConversation = {},
                onOpenChannel = { _, _ -> },
            )
        }
    }

    @Test
    fun roomOpen() = capture("09_room_open") {
        HomeFrame("Чаты", 0) { padding ->
            ChatList(
                conversations = seed.conversations.filter { it.parentId == null }.toImmutableList(),
                typing = persistentSetOf(),
                now = now,
                contentPadding = padding,
                onOpenConversation = {},
                onOpenChannel = { _, _ -> },
                openRooms = persistentSetOf(DemoSeed.ROOM),
            )
        }
    }

    @Test
    fun conversation() = capture("02_conversation") {
        val conversation = seed.conversations.first { it.id == DemoSeed.GROUP }
        ConversationScreen(
            conversation = conversation,
            messages = seed.messages.filter { it.conversationId == conversation.id }.toImmutableList(),
            typing = true,
            authorName = { id -> seed.contacts.firstOrNull { it.id == id }?.name },
            callbacks = ConversationCallbacks({ _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, {}),
        )
    }

    @Test
    fun contacts() = capture("03_contacts") {
        HomeFrame("Контакты", 1) { padding ->
            ContactList(contacts = seed.contacts.take(5).toImmutableList(), contentPadding = padding, onOpenContact = {})
        }
    }

    @Test
    fun settings() = capture("04_settings") {
        HomeFrame("Настройки", 2) { padding ->
            SettingsList(
                demo = true,
                debugUnlocked = true,
                contentPadding = padding,
                onOpenAppearance = {},
                onOpenAbout = {},
                onOpenDebug = {},
            )
        }
    }

    @Test
    fun appearance() = capture("05_appearance") {
        AppearanceScreen(
            appearance = Appearance(theme = theme),
            onChange = {},
            files = AppearanceFileActions(
                hasWallpaper = true,
                onPickWallpaper = {},
                onRemoveWallpaper = {},
                onSaveTheme = {},
                onOpenTheme = {},
            ),
            onBack = {},
        )
    }

    @Test
    fun voice() = capture("06_voice") {
        val room = seed.conversations.first { it.id == DemoSeed.ROOM }
        VoiceChannelScreen(channel = room.channels.first { it.kind == RoomChannelKind.Voice }, onBack = {})
    }

    @Test
    fun about() = capture("07_about") {
        AboutScreen(versionName = "0.2.0", debugUnlocked = false, onUnlockDebug = {}, onBack = {})
    }

    @Test
    fun debug() = capture("08_debug") {
        DebugScreen(
            entries = persistentListOf(
                LogEntry(now.toEpochMilli(), EventCode.AppStarted, 1),
                LogEntry(now.toEpochMilli() + 1200, EventCode.ScreenOpened, 2),
                LogEntry(now.toEpochMilli() + 5400, EventCode.ThemeChanged, 3),
            ),
            crash = CrashReport(now.toEpochMilli(), "java.lang.IllegalStateException\n    at app.blesk.Example.run(Example.kt:12)"),
            appInfo = "blesk 0.2.0",
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
