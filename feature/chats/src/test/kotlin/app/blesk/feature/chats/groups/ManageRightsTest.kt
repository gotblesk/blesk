package app.blesk.feature.chats.groups

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The management editors offer only what the one using them may give, and leave the rest switched off. They open over
 * the whole screen of a phone of common height, not inside the page's scrolling content, where they would lie below it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w400dp-h860dp")
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

    private fun show(page: ManagePage, me: String, state: GroupState = room) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            BleskTheme { ManageScreen(page, env(state, me, scope), now = 0, onBack = {}) }
        }
    }

    private fun env(state: GroupState, me: String, scope: CoroutineScope, gate: IdentityGate? = null) = ManageEnv(
        state = state,
        me = me,
        nameOf = { it },
        change = { change ->
            changes += change
            true
        },
        run = { block -> scope.launch { block() } },
        inviteLink = null,
        gate = gate,
        onOpenPerson = {},
        onGone = {},
        refused = {},
    )

    @Test
    fun aRoleKeeperChangesOnlyRolesBelowWithRightsTheyHave() {
        show(ManagePage.Roles, me = "kira")
        compose.onNodeWithText("Модераторы").assertHasNoClickAction()
        compose.onNodeWithText("Хранители").assertHasNoClickAction()
        compose.onNodeWithText(ROLES_ABOVE).assertExists()
        compose.onNodeWithText("Фанаты").assertHasClickAction().performClick()

        assertOverTheScreen(hasText("Сохранить роль"))
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
        assertOverTheScreen(hasText("Кого сделать админом"))
        compose.onNodeWithText("anya").performClick()

        assertOverTheScreen(hasText("Сделать админом"))
        row("Менять сведения").assertIsOff().assertIsNotEnabled()
        row("Исключать и блокировать").assertIsOn().assertIsEnabled()
        row("Добавлять админов").assertIsOff().assertIsEnabled()
        compose.onNodeWithText("Сделать админом").performClick()
        compose.waitForIdle()
        assertEquals(listOf(GroupChange.Admin("anya", setOf(Right.Invite, Right.Pin, Right.Ban, Right.AssignRoles))), changes)
    }

    @Test
    fun aRoleBringingWhatTheGiverLacksIsShownButNotGiven() {
        show(ManagePage.Members, me = "lera")
        compose.onNodeWithText("anya").performTouchInput { longClick() }
        assertOverTheScreen(hasText("Действия с anya"))
        compose.onNodeWithText("Роли участника").performClick()

        assertOverTheScreen(hasText("anya") and isHeading())
        compose.onNodeWithText(ROLES_LACKING).assertExists()
        compose.onNodeWithText("Модераторы").assertIsOff().assertIsNotEnabled().performClick()
        compose.onNodeWithText("Хранители").assertIsOff().assertIsNotEnabled()
        compose.onNodeWithText("Фанаты").assertIsOff().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(listOf(GroupChange.AssignRole("anya", FANS, on = true)), changes)
    }

    @Test
    fun aMemberMenuOpensOverTheRoomDetails() {
        compose.setContent {
            BleskTheme {
                GroupInfoScreen(
                    state = room,
                    me = "lera",
                    contacts = persistentListOf(),
                    nameOf = { it },
                    actions = GroupActions({}, { _, _, _ -> true }, { true }, { true }, {}),
                    onBack = {},
                )
            }
        }
        compose.onNode(hasText("Участники") and isSelectable()).performClick()
        compose.onNodeWithText("anya").performScrollTo().performTouchInput { longClick() }
        assertOverTheScreen(hasText("Действия с anya"))
        // Anya is no admin yet: what the sheet does is make her one, and it says so.
        compose.onNodeWithText("Сделать админом").performClick()
        assertOverTheScreen(hasText("Сделать админом"))
        compose.onNodeWithText("Сохранить права").assertDoesNotExist()
    }

    @Test
    fun anAdminsRightsAreSavedAsRights() {
        show(ManagePage.Admins, me = "eva")
        compose.onNodeWithText("olga").performClick()
        assertOverTheScreen(hasText("Сохранить права"))
        compose.onNodeWithText("Сделать админом").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w860dp-h400dp")
    fun aRolesButtonsStayInReachOnAShortScreen() {
        show(ManagePage.Roles, me = "eva")
        compose.onNodeWithText("Фанаты").performClick()
        listOf("Сохранить роль", "Удалить роль").forEach { button ->
            compose.onNodeWithText(button).assertIsDisplayed()
            assertTrue(button, compose.onNodeWithText(button).fetchSemanticsNode().boundsInRoot.height > MIN_BUTTON_PX)
        }
    }

    @Test
    fun anEditorWhoseMemberLeftClosesForGood() {
        var state by mutableStateOf(room)
        var sheet by mutableStateOf<ManageSheet?>(ManageSheet.AdminRights("anya"))
        compose.setContent {
            val scope = rememberCoroutineScope()
            BleskTheme { ManageSheets(env(state, "eva", scope), sheet, now = 0, onSheet = { sheet = it }) }
        }
        compose.onNode(hasText("anya", substring = true) and isHeading()).assertExists()
        state = room.copy(members = room.members.filter { it.username != "anya" })
        compose.waitForIdle()
        assertNull(sheet)
    }

    @Test
    fun theSliderWorksAgainAfterTheCodeWasCancelled() {
        var asked = 0
        val gate: IdentityGate = { passed, cancel ->
            LaunchedEffect(Unit) {
                asked++
                if (asked == 1) cancel() else passed()
            }
        }
        compose.setContent {
            val scope = rememberCoroutineScope()
            BleskTheme { ManageScreen(ManagePage.Delete, env(room, "eva", scope, gate), now = 0, onBack = {}) }
        }
        repeat(2) {
            compose.onNode(hasContentDescription("Удалить у всех")).performTouchInput {
                swipeRight(startX = left + KNOB_GRIP_PX, endX = right, durationMillis = 400)
            }
            compose.waitForIdle()
        }
        assertEquals(2, asked)
        assertEquals(listOf<GroupChange>(GroupChange.Dissolve), changes)
    }

    @Test
    fun noRolesAreOfferedByOneWhoStandsAboveNone() {
        // Finn's fans' role may give roles, but it is the lowest: there is none below him to give, and nothing else to do.
        val lowest = room.copy(
            members = room.members + GroupMember("finn", GroupRole.Member, roles = listOf(FANS)),
            roles = room.roles.map { if (it.id == FANS) it.copy(rights = setOf(Right.AssignRoles)) else it },
        )
        show(ManagePage.Members, me = "finn", state = lowest)
        compose.onNodeWithText("anya").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    }

    /** Shown in full on screen and outside the page's scrolling column, which would carry it off with the page. */
    private fun assertOverTheScreen(matcher: SemanticsMatcher) {
        compose.onNode(matcher, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(matcher and hasAnyAncestor(hasScrollAction()), useUnmergedTree = true).assertDoesNotExist()
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

        /** A button squeezed by the list above it is a sliver; one in reach is taller than a finger is wide. */
        const val MIN_BUTTON_PX = 40f
        const val KNOB_GRIP_PX = 30f
    }
}
