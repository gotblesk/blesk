package app.blesk.smoke

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.BleskApplication
import app.blesk.MainActivity
import app.blesk.screenshots.FrozenApplication
import app.blesk.shared.protocol.Invites
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The way to an account from the demo's «Сервер и прокси», on a screen as small as the CI emulator's
 * 320×640 dp. Nothing here reaches a server: the steps that ask one are left to the smoke test.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w320dp-h640dp-mdpi", application = FrozenApplication::class)
class SignUpFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun openServerScreen() {
        compose.enterDemo()
        compose.onNodeWithContentDescription("Настройки").performClick()
        // The row may sit under the bottom lens, so it is pressed through its click action, not a touch.
        compose.onNodeWithText("Сервер и прокси").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /** Where [node]'s middle is, from 0 at the top of the screen to 1 at the bottom. */
    private fun heightShare(node: SemanticsNodeInteraction): Float {
        val bounds = node.getUnclippedBoundsInRoot()
        return (bounds.top + bounds.bottom) / 2f / compose.onRoot().getUnclippedBoundsInRoot().bottom
    }

    @Test
    fun theDemoCardSitsInTheMiddleOfASmallScreen() {
        openServerScreen()

        listOf("Создать аккаунт", "Другой сервер").forEach { text ->
            val share = heightShare(compose.onNodeWithText(text))
            assertTrue("$text at $share of the screen", share in SmokeBand)
        }
        waitFor("Войти по фразе")
    }

    @Test
    fun aBadAddressIsRefusedOnTheServerStep() {
        openServerScreen()
        compose.onNodeWithText("Другой сервер").performClick()
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithContentDescription("Адрес сервера").performTextInput("не адрес")
        compose.onNodeWithText("Готово").performClick()

        waitFor("Не похоже на адрес сервера")
    }

    @Test
    fun anInviteLinkInTheDemoOpensTheInviteStepFilledIn() {
        compose.enterDemo()
        (compose.activity.application as BleskApplication).container.linkRequest.value = Invites.link(CODE)
        compose.mainClock.advanceTimeBy(1_000)

        waitFor("Инвайт-код")
        waitFor(CODE)
    }

    private fun waitFor(text: String) {
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val CODE = "abcdefghjk23"
        const val WAIT_MILLIS = 5_000L

        /** The middle of the screen `scripts/ci/ui.py tap` reaches, clear of the floating lenses. */
        val SmokeBand = 0.15f..0.78f
    }
}
