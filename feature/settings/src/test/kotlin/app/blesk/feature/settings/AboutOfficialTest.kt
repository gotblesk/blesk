package app.blesk.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** «О приложении» leads to the official channel only while the server has one. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AboutOfficialTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(onOpenOfficial: (() -> Unit)?) {
        compose.setContent {
            BleskTheme(motion = BleskMotion(reduced = true)) {
                AboutScreen(
                    versionName = "0.3.0",
                    debugUnlocked = false,
                    onUnlockDebug = {},
                    onOpenLicenses = {},
                    onOpenPolicy = {},
                    onOpenTerms = {},
                    onBack = {},
                    onOpenOfficial = onOpenOfficial,
                )
            }
        }
    }

    @Test
    fun theRowOpensTheChannel() {
        var opened = 0
        show { opened++ }

        compose.onNodeWithText("Официальный канал").performScrollTo().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun noRowWithoutTheChannel() {
        show(onOpenOfficial = null)

        compose.onNodeWithText("Лицензии").assertExists()
        compose.onNodeWithText("Официальный канал").assertDoesNotExist()
    }
}
