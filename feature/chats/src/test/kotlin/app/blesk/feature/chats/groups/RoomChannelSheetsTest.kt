package app.blesk.feature.chats.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.Right
import app.blesk.shared.protocol.RoomRole
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                    actions = GroupActions({}, { _, _, _ -> true }, { true }, { true }, {}),
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

    @Test
    fun aSheetIsPaneOfItsOwnAndThePageBelowIsOutOfReach() {
        showDetails()
        compose.onNodeWithText("черновики").performScrollTo().performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "черновики")).assertExists()
        // TalkBack would otherwise step from the sheet into the covered details and act on them.
        compose.onNodeWithText("Покинуть комнату").assertDoesNotExist()
    }

    @Test
    fun aSheetTitleIsAHeadingOfItsOwnAndATapOnTheSheetKeepsItOpen() {
        val shown = showSheets(room, ChannelTarget.Rights("d"))
        compose.onNode(hasText("черновики") and isHeading()).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithText(RIGHTS_HINT).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNode(hasText("черновики") and isHeading()).performClick()
        assertEquals(ChannelTarget.Rights("d"), shown.target)
        compose.onNode(hasContentDescription("Закрыть")).performClick()
        assertNull(shown.target)
    }

    @Test
    fun theLastTextChannelOffersNoDelete() {
        val talk = room.copy(channels = listOf(GroupChannel("c", "общий"), GroupChannel("v", "эфир", GroupChannelKind.Voice)))
        val shown = showSheets(talk, ChannelTarget.Menu("c"))
        compose.onNodeWithText("Изменить").assertIsDisplayed()
        compose.onNodeWithText("Удалить").assertDoesNotExist()
        shown.target = ChannelTarget.Menu("v")
        compose.onNodeWithText("Удалить").assertIsDisplayed()
    }

    @Test
    fun aChannelNotMadeOrDeletedSaysSo() {
        val shown = showSheets(room, ChannelTarget.Delete("d"), done = false)
        val slider = compose.onNode(hasContentDescription("Удалить канал")).fetchSemanticsNode()
        compose.runOnIdle { slider.config[SemanticsActions.CustomActions].single { it.label == "Удалить канал" }.action() }
        compose.waitForIdle()
        assertEquals(1, shown.refusals)
        shown.target = ChannelTarget.Add
        compose.onNode(hasSetTextAction()).performTextInput("новости")
        compose.onNodeWithText("Создать").performClick()
        compose.waitForIdle()
        assertEquals(2, shown.refusals)
    }

    @Test
    fun aChannelMovesPastTheChannelsItsMoverSees() {
        // Anya's moderators arrange channels, but two of them are shut to them: «Ниже» puts «общий» below «новости».
        val shut = setOf(GroupPolicy.EVERYONE, "mods")
        val arranged = room.copy(
            channels = listOf(
                GroupChannel("d", "черновики", hiddenFrom = shut),
                GroupChannel("c", "общий"),
                GroupChannel("x", "закулисье", hiddenFrom = shut),
                GroupChannel("n", "новости"),
            ),
            roles = listOf(RoomRole("mods", "Модераторы", rights = setOf(Right.Channels))),
        )
        val shown = showSheets(arranged, ChannelTarget.Menu("c"), me = "anya")
        compose.onNodeWithText("Выше").assertDoesNotExist()
        compose.onNodeWithText("Ниже").performClick()
        shown.target = ChannelTarget.Menu("n")
        compose.onNodeWithText("Выше").performClick()
        compose.waitForIdle()
        assertEquals(listOf(GroupChange.MoveChannel("c", 3), GroupChange.MoveChannel("n", 1)), shown.changes)
    }

    @Test
    fun aSheetWhoseChannelIsGoneClosesForGood() {
        val shown = showSheets(room, ChannelTarget.Rights("d"))
        shown.state = room.copy(channels = room.channels.filter { it.id != "d" })
        compose.waitForIdle()
        assertNull(shown.target)
    }

    /** What [showSheets] shows, and what came of it. */
    private class Shown(state: GroupState, target: ChannelTarget?) {
        var state by mutableStateOf(state)
        var target by mutableStateOf(target)
        val changes = mutableListOf<GroupChange>()
        var refusals = 0
    }

    /** [ChannelSheets] alone, as [me]; adding and deleting a channel go through only when [done]. */
    private fun showSheets(state: GroupState, target: ChannelTarget, me: String = "eva", done: Boolean = true): Shown {
        val shown = Shown(state, target)
        compose.setContent {
            val scope = rememberCoroutineScope()
            val env = ManageEnv(
                state = shown.state,
                me = me,
                nameOf = { it },
                change = { change ->
                    shown.changes += change
                    true
                },
                run = { block -> scope.launch { block() } },
                inviteLink = null,
                gate = null,
                onOpenPerson = {},
                onGone = {},
                refused = { shown.refusals++ },
            )
            val actions = GroupActions({}, { _, _, _ -> done }, { done }, { true }, {})
            BleskTheme { ChannelSheets(env, actions, shown.target) { shown.target = it } }
        }
        return shown
    }

    private companion object {
        const val RIGHTS_HINT = "Кто видит канал. Владелец и админы видят все каналы."
    }
}
