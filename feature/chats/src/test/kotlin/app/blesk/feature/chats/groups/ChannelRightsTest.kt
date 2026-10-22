package app.blesk.feature.chats.groups

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.RoomRole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The «Права» sheet of a room channel says who really reads it and changes only that. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ChannelRightsTest {

    @get:Rule
    val compose = createComposeRule()

    private val changes = mutableListOf<GroupChange>()

    private fun show(hiddenFrom: Set<String>) {
        val room = GroupState(
            id = "r",
            kind = GroupKind.Room,
            title = "Студия",
            version = 1,
            epoch = 0,
            members = listOf(GroupMember("eva", GroupRole.Owner), GroupMember("anya", GroupRole.Member, roles = listOf("mods"))),
            channels = listOf(GroupChannel("c", "черновики", hiddenFrom = hiddenFrom)),
            roles = listOf(RoomRole("mods", "Модераторы"), RoomRole("authors", "Авторы")),
        )
        val actions = GroupActions(
            addMembers = {},
            addChannel = { _, _, _ -> },
            removeChannel = {},
            change = { change ->
                changes += change
                true
            },
            leave = {},
        )
        compose.setContent {
            BleskTheme { RoomChannelSheets(room, "eva", actions, ChannelTarget.Rights("c"), onTarget = {}) }
        }
    }

    @Test
    fun whileEveryoneSeesAChannelItsRolesShowOnAndCannotBeSwitched() {
        show(hiddenFrom = setOf("mods"))
        compose.onNodeWithText("Все участники").assertIsOn()
        compose.onNodeWithText("Авторы").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText("Модераторы").assertIsOn().assertIsNotEnabled().performClick()
        compose.onNodeWithText(ROLES_HINT).assertExists()
        compose.waitForIdle()
        assertEquals(emptyList<GroupChange>(), changes)
    }

    @Test
    fun closingAChannelToEveryoneLeavesItOpenToTheRoles() {
        show(hiddenFrom = setOf("mods"))
        compose.onNodeWithText("Все участники").performClick()
        compose.waitForIdle()
        assertEquals(listOf(GroupChange.HideChannel("c", setOf(GroupPolicy.EVERYONE))), changes)
    }

    @Test
    fun aClosedChannelShutsRolesOutOneByOneAndOpensToEveryoneAtOnce() {
        show(hiddenFrom = setOf(GroupPolicy.EVERYONE, "authors"))
        compose.onNodeWithText(ROLES_HINT).assertDoesNotExist()
        compose.onNodeWithText("Все участники").assertIsOff()
        compose.onNodeWithText("Авторы").assertIsOff().assertIsEnabled()
        compose.onNodeWithText("Модераторы").assertIsOn().assertIsEnabled().performClick()
        compose.onNodeWithText("Все участники").performClick()
        compose.waitForIdle()
        assertEquals(
            listOf(
                GroupChange.HideChannel("c", setOf(GroupPolicy.EVERYONE, "authors", "mods")),
                GroupChange.HideChannel("c", emptySet()),
            ),
            changes,
        )
    }

    private companion object {
        const val ROLES_HINT = "Сейчас канал видят все участники. Чтобы открыть его только некоторым ролям, выключи «Все участники»."
    }
}
