package app.blesk.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.AppContainer
import app.blesk.BleskApplication
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.screenshots.FrozenApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Notifications are asked about once, at the first need, with the reason first, and never at the start. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = FrozenApplication::class)
class NotificationAskTest {

    @get:Rule
    val compose = createComposeRule()

    private val container: AppContainer = ApplicationProvider.getApplicationContext<BleskApplication>().container

    @Test
    fun notDueBeforeAnyNeed() {
        assertFalse(notificationAskDue(sdk = 33, needed = false, asked = false, granted = false))
    }

    @Test
    fun notDueBelowAndroid13() {
        assertFalse(notificationAskDue(sdk = 32, needed = true, asked = false, granted = false))
    }

    @Test
    fun notDueOnceAsked() {
        assertFalse(notificationAskDue(sdk = 33, needed = true, asked = true, granted = false))
    }

    @Test
    fun notDueWhenGranted() {
        assertFalse(notificationAskDue(sdk = 33, needed = true, asked = false, granted = true))
    }

    @Test
    fun dueOnTheFirstNeed() {
        assertTrue(notificationAskDue(sdk = 33, needed = true, asked = false, granted = false))
    }

    @Test
    fun theSheetAsksOnceAndRemembersTheAnswer() {
        compose.setContent { BleskTheme(motion = BleskMotion(reduced = true)) { NotificationAsk(container) } }
        compose.onAllNodesWithText(TITLE).fetchSemanticsNodes().let { assertEquals(0, it.size) }

        container.notificationsNeeded.value = true

        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText(TITLE).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(REASON).assertIsDisplayed()
        compose.onNodeWithText("Разрешить").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()

        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText(TITLE).fetchSemanticsNodes().isEmpty() }
        assertTrue(runBlocking { container.onboarding.notificationsAsked.first() })
    }

    private companion object {
        const val TITLE = "Уведомления о сообщениях"
        const val REASON = "Разреши blesk показывать уведомления, чтобы узнавать о сообщениях и звонках, когда он закрыт"
        const val WAIT_MILLIS = 5_000L
    }
}
