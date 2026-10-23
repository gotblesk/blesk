package app.blesk.screenshots

import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Protection
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.chats.ChatList
import app.blesk.feature.chats.Newcomer
import app.blesk.feature.chats.NewcomerChats
import app.blesk.feature.chats.OfficialChannel
import app.blesk.feature.chats.groups.SpaceActions
import app.blesk.feature.chats.groups.SpaceInfoScreen
import app.blesk.feature.settings.AboutScreen
import app.blesk.feature.settings.BuildSignature
import app.blesk.feature.settings.SignedBy
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceMember
import app.blesk.shared.protocol.SpaceRole
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** The server's official channel wherever it shows: on the newcomer screen, in «Чаты», on its card, in «О приложении». */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class OfficialScreenshotTest(private val name: String) {
    private val theme = ThemeSpec.presets.first { it.preset.name.lowercase() == name }
    private val now = Frozen.moment
    private val seed by lazy { DemoSeed.from(ApplicationProvider.getApplicationContext<android.content.Context>().resources, now) }
    private val channel = Conversation(
        id = "space:blesk",
        kind = ConversationKind.Channel,
        title = "blesk",
        protection = Protection.Open,
        preview = null,
        updatedAt = now.minus(Duration.ofHours(3)),
        memberCount = 2_480,
        official = true,
    )

    private fun capture(screen: String, textScale: Float = 1f, content: @Composable () -> Unit) {
        captureFrozen("build/screenshots/${screen}_$name.png") {
            BleskTheme(appearance = Appearance(theme = theme, textScale = textScale), motion = BleskMotion(reduced = true)) { content() }
        }
    }

    @Composable
    private fun Newcomer() {
        ChatsFrame { padding ->
            NewcomerChats(
                me = Newcomer("Ева", "eva", null),
                contentPadding = padding,
                onFind = {},
                onInvite = {},
                official = OfficialChannel("blesk") {},
            )
        }
    }

    @Test
    fun newcomer() = capture("01c_newcomer_official") { Newcomer() }

    @Test
    fun newcomerLarge() = capture("01c_newcomer_official_large", textScale = 1.5f) { Newcomer() }

    @Test
    fun chats() = capture("01g_chats_official") {
        ChatsFrame { padding ->
            val chats = seed.conversations.filter { it.parentId == null && it.kind != ConversationKind.Room && !it.request }.take(4)
            ChatList(
                conversations = (chats.take(2) + channel + chats.drop(2)).toImmutableList(),
                typing = persistentSetOf(),
                now = now,
                contentPadding = padding,
                onOpenConversation = {},
                onOpenChannel = { _, _ -> },
            )
        }
    }

    @Test
    fun about() = capture("07_about_official") {
        AboutScreen(
            versionName = "0.3.0",
            debugUnlocked = false,
            onUnlockDebug = {},
            onOpenLicenses = {},
            onOpenPolicy = {},
            onOpenTerms = {},
            onBack = {},
            signature = BuildSignature(SignedBy.Official, "bd7e78024ea47ca7e7de70c02c39beff5a2855be35560288cdd477e57f9d78bd"),
            onOpenOfficial = {},
        )
    }

    @Test
    fun channelCard() = capture("18c_channel_official") {
        SpaceInfoScreen(
            space = SpaceInfo(
                handle = "blesk",
                kind = SpaceKind.Channel,
                title = "blesk",
                about = "Новости, обновления и подсказки",
                members = 2_480,
                channels = emptyList(),
                role = SpaceRole.Member,
                official = true,
            ),
            me = "eva",
            members = persistentListOf(SpaceMember("egor", SpaceRole.Owner)),
            nameOf = { it },
            actions = SpaceActions({ _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}),
            onBack = {},
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }
    }
}
