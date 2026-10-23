package app.blesk.feature.chats.groups

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
import app.blesk.shared.protocol.GroupInvite
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
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

    private fun show(me: String, state: GroupState = group) {
        compose.setContent {
            BleskTheme {
                GroupInfoScreen(
                    state = state,
                    me = me,
                    contacts = persistentListOf(),
                    nameOf = { it },
                    actions = GroupActions({}, { _, _, _ -> true }, { true }, { true }, { left++ }),
                    onBack = {},
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

    private companion object {
        const val HAND_OVER_FIRST = "Чтобы выйти, сначала передай владение: кто-то должен остаться за главного."
    }
}
