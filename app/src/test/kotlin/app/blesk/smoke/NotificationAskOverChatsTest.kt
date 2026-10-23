package app.blesk.smoke

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.AppContainer
import app.blesk.BleskApplication
import app.blesk.MainActivity
import app.blesk.screenshots.FrozenApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The question about notifications comes by itself over the chats: TalkBack reaches only the question then. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi", application = FrozenApplication::class)
class NotificationAskOverChatsTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val container: AppContainer
        get() = (compose.activity.application as BleskApplication).container

    private fun waitFor(text: String) {
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theChatsUnderTheQuestionAreOutOfTalkBacksReach() {
        compose.enterDemo()
        waitFor(CHAT)

        container.notificationsNeeded.value = true

        waitFor(TITLE)
        // The tree as TalkBack reads it, where a cleared page has nothing left under it.
        compose.onAllNodesWithText(CHAT).assertCountEquals(0)

        compose.onNodeWithText("Отмена").performClick()
        waitFor(CHAT)
    }

    private companion object {
        const val CHAT = "Эхо"
        const val TITLE = "Уведомления о сообщениях"
        const val WAIT_MILLIS = 5_000L
    }
}
