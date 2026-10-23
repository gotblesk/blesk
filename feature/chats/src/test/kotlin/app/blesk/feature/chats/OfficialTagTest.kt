package app.blesk.feature.chats

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Protection
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.chats.groups.SpaceActions
import app.blesk.feature.chats.groups.SpaceInfoScreen
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceRole
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** «Официальный» marks the server's own channel in the list and on its card, and no other. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class OfficialTagTest {

    @get:Rule
    val compose = createComposeRule()

    private fun list(official: Boolean) {
        val channel = Conversation(
            id = "space:blesk",
            kind = ConversationKind.Channel,
            title = "blesk",
            protection = Protection.Open,
            preview = null,
            updatedAt = Instant.EPOCH,
            official = official,
        )
        compose.setContent {
            BleskTheme(motion = BleskMotion(reduced = true)) {
                ChatList(
                    conversations = persistentListOf(channel),
                    typing = persistentSetOf(),
                    now = Instant.EPOCH,
                    contentPadding = PaddingValues(),
                    onOpenConversation = {},
                    onOpenChannel = { _, _ -> },
                )
            }
        }
    }

    private fun card(official: Boolean) {
        compose.setContent {
            BleskTheme(motion = BleskMotion(reduced = true)) {
                SpaceInfoScreen(
                    space = SpaceInfo("blesk", SpaceKind.Channel, "blesk", "", 2, emptyList(), SpaceRole.Member, official = official),
                    me = "eva",
                    members = persistentListOf(),
                    nameOf = { it },
                    actions = SpaceActions({ _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}),
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun theOfficialChannelIsMarkedInTheList() {
        list(official = true)

        compose.onNodeWithText("Официальный").assertIsDisplayed()
        compose.onNodeWithText("канал").assertDoesNotExist()
    }

    @Test
    fun anotherChannelIsJustAChannel() {
        list(official = false)

        compose.onNodeWithText("канал").assertIsDisplayed()
        compose.onNodeWithText("Официальный").assertDoesNotExist()
    }

    @Test
    fun theOfficialChannelsCardSaysSo() {
        card(official = true)

        compose.onNodeWithText("Официальный").assertIsDisplayed()
        compose.onNodeWithText("без шифрования").assertIsDisplayed()
    }

    @Test
    fun anotherChannelsCardDoesNot() {
        card(official = false)

        compose.onNodeWithText("Официальный").assertDoesNotExist()
    }
}
