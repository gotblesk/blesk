package app.blesk.feature.chats.groups

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
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
import org.robolectric.annotation.Config

/** A channel's sheets opened from the room's details lie over the whole screen, not inside its scrolling content. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w400dp-h860dp")
class RoomChannelSheetsTest {

    @get:Rule
    val compose = createComposeRule()

    private val room = GroupState(
        id = "r",
        kind = GroupKind.Room,
        title = "Студия",
        version = 1,
        epoch = 0,
        members = listOf(GroupMember("eva", GroupRole.Owner), GroupMember("anya", GroupRole.Member, roles = listOf("mods"))),
        channels = listOf(GroupChannel("c", "общий"), GroupChannel("d", "черновики", hiddenFrom = setOf(GroupPolicy.EVERYONE))),
        roles = listOf(RoomRole("mods", "Модераторы")),
    )

    private fun showDetails() {
        compose.setContent {
            BleskTheme {
                GroupInfoScreen(
                    state = room,
                    me = "eva",
                    contacts = persistentListOf(),
                    nameOf = { it },
                    actions = GroupActions({}, { _, _, _ -> }, {}, { true }, {}),
                    onBack = {},
                )
            }
        }
    }

    /** Shown in full on screen and outside the details' scrolling column, which would carry it off with the page. */
    private fun assertOverTheScreen(matcher: SemanticsMatcher) {
        compose.onNode(matcher, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(matcher and hasAnyAncestor(hasScrollAction()), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun rightsOpenOverTheDetails() {
        showDetails()
        compose.onNodeWithText("черновики").performScrollTo().performClick()
        assertOverTheScreen(hasText("Права"))
        compose.onNodeWithText("Права").performClick()
        assertOverTheScreen(hasText(RIGHTS_HINT))
        compose.onNodeWithText("Модераторы").assertIsDisplayed()
    }

    @Test
    fun aNewChannelOpensOverTheDetails() {
        showDetails()
        compose.onNodeWithText("+ Канал").performScrollTo().performClick()
        assertOverTheScreen(hasText("Новый канал"))
    }

    private companion object {
        const val RIGHTS_HINT = "Кто видит канал. Владелец и админы видят все каналы."
    }
}
