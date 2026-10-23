package app.blesk.core.design.component

import android.view.View
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.glass.BottomLens
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A page ends at the keyboard: what is typed stays in sight, and the room under the page is not counted twice.
 * Its end scrolls up to the middle of the screen, where the CI smoke test taps.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w320dp-h640dp-mdpi")
class ScrollScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var view: View

    private fun show(bottom: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent {
            view = LocalView.current
            BleskTheme(motion = BleskMotion(reduced = true)) { ScrollScreen("Профиль", bottom = bottom) { content() } }
        }
    }

    /** The keyboard comes up, [Keyboard] tall at mdpi, where one pixel is one dp. */
    private fun openKeyboard() {
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, Keyboard.value.toInt()))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        compose.waitForIdle()
    }

    private fun scrollToTheEnd() {
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()
    }

    private val screenHeight: Dp get() = compose.onRoot().getUnclippedBoundsInRoot().bottom

    @Test
    fun aFieldBeingTypedInStaysAboveTheKeyboard() {
        show {
            Spacer(Modifier.height(420.dp))
            TextInput("Имя", rememberTextFieldState())
        }
        compose.onNodeWithContentDescription("Имя").performClick()
        openKeyboard()
        val field = compose.onNodeWithContentDescription("Имя").getUnclippedBoundsInRoot()
        assertTrue("field ends at ${field.bottom}", field.bottom <= screenHeight - Keyboard)
    }

    @Test
    fun theEndOfAPageMeetsALensRidingOnTheKeyboard() {
        show(bottom = { BottomLens { Text("Линза") } }) {
            Spacer(Modifier.height(1_000.dp))
            Text("Конец")
        }
        openKeyboard()
        scrollToTheEnd()
        val end = compose.onNodeWithText("Конец").getUnclippedBoundsInRoot()
        val lens = compose.onNodeWithText("Линза").getUnclippedBoundsInRoot()
        assertTrue("lens text at ${lens.top}, page ends at ${end.bottom}", lens.top - end.bottom in EndRoom..EndRoom + Gap)
    }

    @Test
    fun aBarLeftUnderTheKeyboardTakesNoRoom() {
        show(bottom = { Text("Под клавиатурой", Modifier.height(120.dp)) }) {
            Spacer(Modifier.height(1_000.dp))
            Text("Конец")
        }
        openKeyboard()
        scrollToTheEnd()
        val end = compose.onNodeWithText("Конец").getUnclippedBoundsInRoot()
        val keyboardTop = screenHeight - Keyboard
        assertTrue("keyboard at $keyboardTop, page ends at ${end.bottom}", keyboardTop - end.bottom in EndRoom..EndRoom + Gap)
    }

    @Test
    fun theLastButtonRisesToTheMiddleOfTheScreen() {
        show {
            repeat(20) { Text("Строка $it", Modifier.height(48.dp)) }
            Button("Последняя", onClick = {})
        }
        scrollToTheEnd()
        val button = compose.onNodeWithText("Последняя").getUnclippedBoundsInRoot()
        val centre = (button.top + button.bottom) / 2
        assertTrue("button centre at $centre of $screenHeight", centre <= screenHeight * SmokeBand)
    }

    private companion object {
        val Keyboard = 300.dp

        /** A quarter of the 640 dp screen, left under the last control. */
        val EndRoom = 160.dp

        /** The lens's padding and a little more; a page counting the keyboard twice ends far above. */
        val Gap = 40.dp

        /** The lowest a control's centre may sit for `scripts/ci/ui.py` to tap it, as a share of the screen. */
        const val SmokeBand = 0.78f
    }
}
