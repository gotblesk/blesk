package app.blesk.smoke

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

/** From the welcome screen, where every test's app starts, into the demo's chats. */
internal fun AndroidComposeTestRule<*, *>.enterDemo() {
    waitUntil(WAIT_MILLIS) { onAllNodesWithText(DEMO).fetchSemanticsNodes().isNotEmpty() }
    onNodeWithText(DEMO).performClick()
    // The ways in part before the choice is carried out, then the chats come in.
    mainClock.advanceTimeBy(SETTLE_MILLIS)
    waitForIdle()
}

private const val DEMO = "Попробовать демо"
private const val WAIT_MILLIS = 5_000L
private const val SETTLE_MILLIS = 2_000L
