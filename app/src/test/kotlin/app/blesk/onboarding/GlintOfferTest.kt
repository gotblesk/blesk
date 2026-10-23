package app.blesk.onboarding

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The last setup step offers the server's official channel ticked, and ends with the person's choice. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class GlintOfferTest {

    @get:Rule
    val compose = createComposeRule()

    private var finished: Result<String?>? = null

    private fun show(channel: String?) {
        compose.setContent {
            BleskTheme(motion = BleskMotion(reduced = true)) {
                GlintStep(flowOf(channel), onFinish = { finished = Result.success(it) })
            }
        }
    }

    @Test
    fun theChannelIsOfferedTickedAndSubscribedTo() {
        show("blesk")

        compose.onNodeWithText(SUBSCRIBE).performScrollTo().assertIsOn()
        compose.onNodeWithText(DONE).performClick()

        assertEquals("blesk", finished?.getOrThrow())
    }

    @Test
    fun untickedTheChannelIsLeftAlone() {
        show("blesk")

        compose.onNodeWithText(SUBSCRIBE).performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText(DONE).performClick()

        assertNull(finished?.getOrThrow())
        assertEquals(true, finished?.isSuccess)
    }

    @Test
    fun aServerWithoutAChannelOffersNone() {
        show(channel = null)

        compose.onNodeWithText(SUBSCRIBE).assertDoesNotExist()
        compose.onNodeWithText(DONE).performClick()

        assertEquals(true, finished?.isSuccess)
        assertNull(finished?.getOrThrow())
    }

    private companion object {
        const val SUBSCRIBE = "Подписаться на канал blesk"
        const val DONE = "В чаты"
    }
}
