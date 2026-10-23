package app.blesk.feature.chats.groups

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.model.Contact
import app.blesk.core.design.theme.BleskTheme
import app.blesk.shared.protocol.GroupInvite
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the details offer: only what goes through, and nothing that leaves the group without anyone to run it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w400dp-h860dp")
class GroupInfoActionsTest {

    @get:Rule
    val compose = createComposeRule()

    private var left = 0

    private val group = GroupState(
        id = "g",
        kind = GroupKind.Group,
        title = "Выходные",
        version = 1,
        epoch = 0,
        members = listOf(GroupMember("eva", GroupRole.Owner), GroupMember("anya", GroupRole.Member)),
        invites = listOf(GroupInvite("abcdefgh", "Друзья")),
    )

    private fun show(
        me: String,
        state: GroupState = group,
        contacts: List<Contact> = emptyList(),
        extras: GroupInfoExtras = GroupInfoExtras(),
    ) {
        compose.setContent {
            BleskTheme {
                GroupInfoScreen(
                    state = state,
                    me = me,
                    contacts = contacts.toImmutableList(),
                    nameOf = { it },
                    actions = GroupActions({}, { _, _, _ -> true }, { true }, { true }, { left++ }),
                    onBack = {},
                    extras = extras,
                )
            }
        }
    }

    @Test
    fun theOwnerHandsTheGroupOverBeforeLeavingIt() {
        show(me = "eva")
        compose.onNodeWithText("Покинуть группу").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0, left)
        compose.onNodeWithText(HAND_OVER_FIRST).assertIsDisplayed()
        compose.onNodeWithText("anya").assertIsDisplayed()
    }

    @Test
    fun anOwnerAloneLeavesAtOnce() {
        show(me = "eva", state = group.copy(members = group.members.take(1)))
        compose.onNodeWithText("Покинуть группу").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, left)
    }

    @Test
    fun aMemberSharesTheLinksThereAreAndMakesNone() {
        // Anya may invite, as every member may, but no phone takes a state from one who manages nothing.
        show(me = "anya")
        compose.onNodeWithText("Ещё").performClick()
        compose.onNodeWithText("Добавить участников").assertDoesNotExist()
        compose.onNodeWithText("Покинуть группу").assertExists()
        compose.onNodeWithContentDescription("Закрыть").performClick()

        compose.onNodeWithText("Пригласить").performClick()
        compose.onNodeWithText("Друзья").assertIsDisplayed()
        compose.onNodeWithText("Новая ссылка").assertDoesNotExist()
        compose.onNodeWithText("Друзья").performClick()
        compose.onNodeWithText("Отозвать").assertDoesNotExist()
    }

    @Test
    fun aMembersLinkSendsTheJoinerToOneWhoMayLetThemIn() {
        // Anya manages nothing: a request to join sent to her would wait for good, as her phone makes no state.
        show(me = "anya", extras = GroupInfoExtras(inviteLink = { admitter, code -> "$admitter/$code" }))
        compose.onNodeWithText("Пригласить").performClick()
        compose.onNodeWithText("Друзья").performClick()
        compose.onNodeWithText("eva/abcdefgh").assertIsDisplayed()
    }

    @Test
    fun aLongContactListKeepsTheAddButtonInReach() {
        show(me = "eva", contacts = (1..CONTACTS).map { Contact("c$it", "Контакт $it", "c$it") })
        compose.onNodeWithText("Ещё").performClick()
        compose.onNodeWithText("Добавить участников").performClick()
        val add = compose.onNodeWithText("Добавить").fetchSemanticsNode().boundsInRoot
        assertTrue("$add", add.height >= with(compose.density) { TOUCH_TARGET.toPx() })
    }

    private companion object {
        const val CONTACTS = 14
        val TOUCH_TARGET = 48.dp
        const val HAND_OVER_FIRST = "Чтобы выйти, сначала передай владение: кто-то должен остаться за главного."
    }
}
