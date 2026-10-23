package app.blesk.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.settings.NotificationSettings
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.chats.ChatList
import app.blesk.feature.settings.NotificationsScreen
import app.blesk.onboarding.NotificationAskSheet
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The question about notifications over the chats, and «Уведомления» while Android does not allow them. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class NotificationScreenshotTest(private val name: String) {
    private val theme = ThemeSpec.presets.first { it.preset.name.lowercase() == name }
    private val now = Frozen.moment
    private val seed by lazy { DemoSeed.from(ApplicationProvider.getApplicationContext<android.content.Context>().resources, now) }

    private fun capture(screen: String, textScale: Float = 1f, content: @Composable () -> Unit) {
        captureFrozen("build/screenshots/${screen}_$name.png") {
            BleskTheme(appearance = Appearance(theme = theme, textScale = textScale), motion = BleskMotion(reduced = true)) { content() }
        }
    }

    @Test
    fun blocked() = capture("25b_notifications_blocked") {
        NotificationsScreen(settings = NotificationSettings(), serverConnected = true, onChange = {}, onBack = {}, blocked = true)
    }

    @Test
    fun blockedLarge() = capture("25b_notifications_blocked_large", textScale = 1.5f) {
        NotificationsScreen(settings = NotificationSettings(), serverConnected = true, onChange = {}, onBack = {}, blocked = true)
    }

    @Test
    fun ask() = capture("25c_notification_ask") {
        Box(Modifier.fillMaxSize()) {
            ChatsFrame { padding ->
                ChatList(
                    conversations = seed.conversations.filter { it.parentId == null && it.kind != ConversationKind.Room && !it.request }
                        .take(3)
                        .toImmutableList(),
                    typing = persistentSetOf(),
                    now = now,
                    contentPadding = padding,
                    onOpenConversation = {},
                    onOpenChannel = { _, _ -> },
                )
            }
            NotificationAskSheet(onAllow = {}, onAnswer = {})
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }
    }
}
