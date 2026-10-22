package app.blesk.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.chats.groups.ChannelTarget
import app.blesk.feature.chats.groups.GroupActions
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A room channel's «Права» sheet, open to everyone and closed to everyone but one role. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class RoomScreenshotTest(private val name: String) {
    private val theme = ThemeSpec.presets.first { it.preset.name.lowercase() == name }

    private fun capture(screen: String, content: @Composable () -> Unit) {
        captureFrozen("build/screenshots/${screen}_$name.png") {
            BleskTheme(appearance = Appearance(theme = theme), motion = BleskMotion(reduced = true)) { content() }
        }
    }

    @Test
    fun channelRightsOpen() = capture("51_channel_rights_open") { ChannelRights(hiddenFrom = emptySet()) }

    @Test
    fun channelRightsClosed() = capture("51_channel_rights_closed") {
        ChannelRights(hiddenFrom = setOf(GroupPolicy.EVERYONE, "authors"))
    }

    /** On the bare background, so the sheet itself is what the picture shows. */
    @Composable
    private fun ChannelRights(hiddenFrom: Set<String>) {
        val state = GroupState(
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
        val actions = GroupActions({}, { _, _, _ -> }, {}, { true }, {})
        Box(Modifier.fillMaxSize().background(Blesk.colors.background)) {
            RoomChannelSheets(state, "eva", actions, ChannelTarget.Rights("drafts"), onTarget = {})
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeSpec.presets.map { arrayOf(it.preset.name.lowercase()) }
    }
}
