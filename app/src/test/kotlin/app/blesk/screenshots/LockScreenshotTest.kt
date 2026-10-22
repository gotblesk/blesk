package app.blesk.screenshots

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.settings.ChatLocks
import app.blesk.core.data.settings.LockRules
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.chats.ChatList
import app.blesk.feature.chats.LocalChatLocks
import app.blesk.feature.chats.locks.ChatLocksActions
import app.blesk.feature.chats.locks.ChatLocksScreen
import app.blesk.feature.chats.locks.LockGlass
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Chats under a lock: the list, the frost over a locked chat, the settings of locks. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class LockScreenshotTest(private val name: String) {
    private val theme = ThemeSpec.presets.first { it.preset.name.lowercase() == name }
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Frozen.moment
    private val seed by lazy { DemoSeed.from(context.resources, now) }

    private fun capture(screen: String, content: @Composable () -> Unit) {
        captureFrozen("build/screenshots/${screen}_$name.png") {
            BleskTheme(appearance = Appearance(theme = theme), motion = BleskMotion(reduced = true)) { content() }
        }
    }

    @Test
    fun lockedChats() = capture("01_chats_locked") {
        val conversations = seed.conversations.filter { it.parentId == null && it.kind != ConversationKind.Room && !it.request }
        val saved = conversations.filter { it.kind == ConversationKind.Saved }.map { it.id }
        CompositionLocalProvider(LocalChatLocks provides ChatLocks(locked = setOf(DemoSeed.ANYA, ChatLocks.ARCHIVE) + saved)) {
            LensScaffold(top = { TopLens(title = "Чаты") }) { padding ->
                ChatList(
                    conversations = conversations.map { if (it.id == DemoSeed.ANYA) it.copy(unread = 3) else it }.toImmutableList(),
                    typing = persistentSetOf(),
                    now = now,
                    contentPadding = padding,
                    onOpenConversation = {},
                    onOpenChannel = { _, _ -> },
                    archivedCount = 2,
                    onOpenArchive = {},
                )
            }
        }
    }

    @Test
    fun lockGlass() = capture("01_chat_lock_glass") {
        LockGlass(thaw = 0f, dimmed = false, onBiometric = {}, onCode = {}) {
            LensScaffold(top = { TopLens(title = "Аня Соколова") }) { }
        }
    }

    @Test
    fun locksSettings() = capture("45_locks") {
        ChatLocksScreen(
            rules = LockRules(),
            lockedChats = 3,
            archiveLocked = true,
            separateCodeSet = false,
            actions = ChatLocksActions({}, {}, {}, {}),
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }
    }
}
