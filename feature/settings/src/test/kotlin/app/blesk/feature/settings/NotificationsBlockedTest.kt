package app.blesk.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.settings.NotificationSettings
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** After a refusal «Уведомления» says so first and leads to where they are allowed; not in the demo, not when allowed. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class NotificationsBlockedTest {

    @get:Rule
    val compose = createComposeRule()

    private var allowed = 0

    private fun show(blocked: Boolean, server: Boolean = true) {
        compose.setContent {
            BleskTheme(motion = BleskMotion(reduced = true)) {
                NotificationsScreen(
                    settings = NotificationSettings(),
                    serverConnected = server,
                    onChange = {},
                    onBack = {},
                    blocked = blocked,
                    onAllow = { allowed++ },
                )
            }
        }
    }

    @Test
    fun aRefusalLeadsToTheSettings() {
        show(blocked = true)

        compose.onNodeWithText(BLOCKED).performClick()

        assertEquals(1, allowed)
    }

    @Test
    fun nothingToAllowWhenAllowed() {
        show(blocked = false)

        compose.onNodeWithText("Уведомлять о сообщениях").assertExists()
        compose.onNodeWithText(BLOCKED).assertDoesNotExist()
    }

    @Test
    fun theDemoHasNothingToAllow() {
        show(blocked = true, server = false)

        compose.onNodeWithText(BLOCKED).assertDoesNotExist()
    }

    private companion object {
        const val BLOCKED = "Уведомления выключены"
    }
}
