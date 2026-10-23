package app.blesk.feature.chats

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The chat in a theme's preview is a picture: TalkBack hears what it is, and nothing in it answers a tap. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ChatSampleTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aSampleIsAPictureOfAChatNotAChat() {
        compose.setContent { BleskTheme(motion = BleskMotion(reduced = true)) { ChatSample() } }
        compose.onNodeWithContentDescription("Пример переписки в этой теме").assertExists()
        compose.onAllNodesWithText("Да, сразу везде").assertCountEquals(0)
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
