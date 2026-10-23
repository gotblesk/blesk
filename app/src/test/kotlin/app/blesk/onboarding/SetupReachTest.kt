package app.blesk.onboarding

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.feature.security.PhraseActions
import app.blesk.feature.security.PhraseScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The phrase step on the CI emulator's 320×640 dp screen: «Позже» can be brought to where the smoke test taps. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w320dp-h640dp-mdpi")
class SetupReachTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theLaterOfTheFreshPhraseRisesIntoTheMiddle() {
        compose.setContent {
            BleskTheme(motion = BleskMotion(reduced = true)) {
                PhraseScreen(
                    words = WORDS,
                    actions = PhraseActions(onCopy = {}, onPasswordManager = null, onChecked = {}, onLater = {}, onBack = null),
                )
            }
        }
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()

        val later = compose.onNodeWithText("Позже").getUnclippedBoundsInRoot()
        val share = (later.top + later.bottom) / 2f / compose.onRoot().getUnclippedBoundsInRoot().bottom
        assertTrue("«Позже» at $share of the screen at the end of the page", share in SmokeBand)
    }

    private companion object {
        val WORDS = "берег ветер голос дождь ёлка живот замок искра кошка лампа мост нитка".split(" ")

        /** The middle of the screen `scripts/ci/ui.py tap` reaches, clear of the floating lenses. */
        val SmokeBand = 0.15f..0.78f
    }
}
