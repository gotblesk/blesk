package app.blesk.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.chats.groups.ChannelTarget
import app.blesk.feature.chats.groups.GroupActions
import app.blesk.feature.chats.groups.GroupInfoScreen
import app.blesk.feature.chats.groups.RoomChannelSheets
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.RoomRole
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A room channel's «Права» sheet, open to everyone and closed to everyone but one role, and opened
 * the way people open it: from the room's details, over them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class RoomScreenshotTest(private val name: String) {
    @get:Rule
    val compose = createComposeRule()

    private val theme = ThemeSpec.presets.first { it.preset.name.lowercase() == name }

    private fun capture(screen: String, content: @Composable () -> Unit) {
        captureFrozen("build/screenshots/${screen}_$name.png") { Themed(content) }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        BleskTheme(appearance = Appearance(theme = theme), motion = BleskMotion(reduced = true)) { content() }
    }

    @Test
    fun channelRightsOpen() = capture("51_channel_rights_open") { ChannelRights(hiddenFrom = emptySet()) }

    @Test
    fun channelRightsClosed() = capture("51_channel_rights_closed") {
        ChannelRights(hiddenFrom = setOf(GroupPolicy.EVERYONE, "authors"))
    }

    @Test
    fun channelRightsOverDetails() = compose.captureFrozen(
        "build/screenshots/51_channel_rights_details_$name.png",
        act = {
            onNodeWithText("черновики").performScrollTo().performClick()
            onNodeWithText("Права").performClick()
        },
    ) {
        Themed {
            GroupInfoScreen(
                state = studio(hiddenFrom = setOf(GroupPolicy.EVERYONE, "authors")),
                me = "eva",
                contacts = persistentListOf(),
                nameOf = { mapOf("eva" to "Ева", "lera" to "Лера", "anya" to "Аня")[it] ?: it },
                actions = actions,
                onBack = {},
            )
        }
    }

    /** On the bare background, so the sheet itself is what the picture shows. */
    @Composable
    private fun ChannelRights(hiddenFrom: Set<String>) {
        Box(Modifier.fillMaxSize().background(Blesk.colors.background)) {
            RoomChannelSheets(studio(hiddenFrom), "eva", actions, ChannelTarget.Rights("drafts"), onTarget = {})
        }
    }

    private val actions = GroupActions({}, { _, _, _ -> true }, { true }, { true }, {})

    private fun studio(hiddenFrom: Set<String>) = GroupState(
        id = "s",
        kind = GroupKind.Room,
        title = "Студия",
        version = 1,
        epoch = 0,
        members = listOf(
            GroupMember("eva", GroupRole.Owner),
            GroupMember("lera", GroupRole.Member, roles = listOf("mods")),
            GroupMember("anya", GroupRole.Member, roles = listOf("authors")),
        ),
        channels = listOf(GroupChannel("general", "общий"), GroupChannel("drafts", "черновики", hiddenFrom = hiddenFrom)),
        roles = listOf(RoomRole("mods", "Модераторы", hue = 3), RoomRole("authors", "Авторы", hue = 6)),
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }
    }
}
