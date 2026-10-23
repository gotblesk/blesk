package app.blesk.feature.chats

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.shared.appearance.Appearance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The newcomer screen with the server's official channel: a row under the ways in, not a friend flying in. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class NewcomerOfficialTest {

    @get:Rule
    val compose = createComposeRule()

    private var opened = 0

    private fun show(official: OfficialChannel?, textScale: Float = 1f) {
        compose.setContent {
            BleskTheme(appearance = Appearance(textScale = textScale), motion = BleskMotion(reduced = true)) {
                NewcomerChats(
                    me = Newcomer("Ева", "eva", null),
                    contentPadding = PaddingValues(),
                    onFind = {},
                    onInvite = {},
                    official = official,
                )
            }
        }
    }

    @Test
    fun theOfficialRowOpensTheChannel() {
        show(OfficialChannel("blesk") { opened++ })

        compose.onNodeWithText("Новости и подсказки blesk").assertIsDisplayed()
        compose.onNodeWithText("Официальный").assertIsDisplayed()
        compose.onNodeWithText("blesk").performClick()

        assertEquals(1, opened)
        compose.onNodeWithText("Позвать друга").assertIsDisplayed()
    }

    @Test
    fun noRowWithoutTheChannel() {
        show(official = null)

        compose.onNodeWithText("Позвать друга").assertIsDisplayed()
        compose.onNodeWithText("Новости и подсказки blesk").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun theRowIsReachedAtLargeTextOnASmallScreen() {
        show(OfficialChannel("blesk") { opened++ }, textScale = 1.5f)

        compose.onNodeWithText("Новости и подсказки blesk").performScrollTo().assertIsDisplayed()
    }
}
