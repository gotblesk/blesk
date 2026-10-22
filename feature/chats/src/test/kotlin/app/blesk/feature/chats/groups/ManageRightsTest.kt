package app.blesk.feature.chats.groups

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.Right
import app.blesk.shared.protocol.RoomRole
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The management editors offer only what the one using them may give, and leave the rest switched off. */
@RunWith(AndroidJUnit4::class)
// Tall enough that every member and every sheet's buttons are on screen to be touched.
@Config(sdk = [36], qualifiers = "w360dp-h1600dp")
class ManageRightsTest {

    @get:Rule
    val compose = createComposeRule()

    private val changes = mutableListOf<GroupChange>()

    /**
     * Lera is an admin who may add admins, ban and give roles; Olga is another admin. Kira's keepers' role may change
     * roles and ranks under the moderators', who may delete others' messages; the drafts channel is shut to the keepers.
     */
    private val room = GroupState(
        id = "r",
        kind = GroupKind.Room,
        title = "Студия",
        version = 1,
        epoch = 0,
        members = listOf(
            GroupMember("eva", GroupRole.Owner),
            GroupMember("lera", GroupRole.Admin, rights = setOf(Right.AddAdmins, Right.Ban, Right.AssignRoles)),
            GroupMember("olga", GroupRole.Admin),
            GroupMember("kira", GroupRole.Member, roles = listOf(KEEPERS)),
            GroupMember("anya", GroupRole.Member),
        ),
        channels = listOf(GroupChannel("c", "общее"), GroupChannel("d", "черновики", hiddenFrom = setOf(GroupPolicy.EVERYONE, KEEPERS))),
        roles = listOf(
            RoomRole("mods", "Модераторы", rights = setOf(Right.DeleteOthers)),
            RoomRole(KEEPERS, "Хранители", rights = setOf(Right.Roles)),
            RoomRole(FANS, "Фанаты"),
        ),
    )

    private fun show(page: ManagePage, me: String) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val env = ManageEnv(
                state = room,
                me = me,
                nameOf = { it },
                change = { change ->
                    changes += change
                    true
                },
                run = { block -> scope.launch { block() } },
                inviteLink = null,
                gate = null,
                onOpenPerson = {},
                onGone = {},
                refused = {},
            )
            BleskTheme { ManageScreen(page, env, now = 0, onBack = {}) }
        }
    }

    @Test
    fun aRoleKeeperChangesOnlyRolesBelowWithRightsTheyHave() {
        show(ManagePage.Roles, me = "kira")
        compose.onNodeWithText("Модераторы").assertHasNoClickAction()
        compose.onNodeWithText("Хранители").assertHasNoClickAction()
        compose.onNodeWithText(ROLES_ABOVE).assertExists()
        compose.onNodeWithText("Фанаты").assertHasClickAction().performClick()

        compose.onNodeWithText(RIGHTS_LACKING).assertExists()
        row("Исключать и блокировать").assertIsOff().assertIsNotEnabled().performClick()
        row("Закреплять").assertIsOff().assertIsEnabled().performClick()
        compose.onNodeWithText("Сохранить роль").performClick()
        compose.waitForIdle()
        assertEquals(listOf(GroupChange.SaveRole(RoomRole(FANS, "Фанаты", rights = setOf(Right.Pin)))), changes)
    }

    @Test
    fun anAdminAppointsMembersOnlyWithRightsTheyHave() {
        show(ManagePage.Admins, me = "lera")
        compose.onNodeWithText("olga").assertIsNotEnabled()
        compose.onNodeWithText("Добавить администратора").performClick()
        compose.onNodeWithText("anya").performClick()

        row("Менять сведения").assertIsOff().assertIsNotEnabled()
        row("Исключать и блокировать").assertIsOn().assertIsEnabled()
        row("Добавлять админов").assertIsOff().assertIsEnabled()
        compose.onNodeWithText("Сохранить права").performClick()
        compose.waitForIdle()
        assertEquals(listOf(GroupChange.Admin("anya", setOf(Right.Invite, Right.Pin, Right.Ban, Right.AssignRoles))), changes)
    }

    @Test
    fun aRoleBringingWhatTheGiverLacksIsShownButNotGiven() {
        show(ManagePage.Members, me = "lera")
        compose.onNodeWithText("anya").performTouchInput { longClick() }
        compose.onNodeWithText("Роли участника").performClick()

        compose.onNodeWithText(ROLES_LACKING).assertExists()
        compose.onNodeWithText("Модераторы").assertIsOff().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Хранители").assertIsOff().assertIsNotEnabled()
        compose.onNodeWithText("Фанаты").assertIsOff().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(listOf(GroupChange.AssignRole("anya", FANS, on = true)), changes)
    }

    /**
     * The switch [title] heads, scrolled into a sheet's view first. A sheet merges its scrolling column into itself, so
     * only the title's own node knows how far that column shows.
     */
    private fun row(title: String): SemanticsNodeInteraction {
        compose.onNodeWithText(title, useUnmergedTree = true).performScrollTo()
        return compose.onNodeWithText(title)
    }

    private companion object {
        const val KEEPERS = "keepers"
        const val FANS = "fans"
        const val ROLES_ABOVE = "Свою роль и роли выше неё меняют только те, кто выше тебя."
        const val RIGHTS_LACKING = "Право, которого нет у тебя самого, дать нельзя."
        const val ROLES_LACKING = "Роль с правами или каналами, которых нет у тебя, дать нельзя."
    }
}
