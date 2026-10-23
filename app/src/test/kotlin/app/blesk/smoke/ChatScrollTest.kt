package app.blesk.smoke

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.MainActivity
import app.blesk.screenshots.FrozenApplication
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The date capsule that shows while a chat is scrolled hangs under the top lens. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi", application = FrozenApplication::class)
class ChatScrollTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun dateUnderTheTopLens() {
        compose.enterDemo()
        compose.onNodeWithText("Аня Соколова").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Давай в четыре у моста").performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f))
            moveBy(Offset(0f, 200f))
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onRoot().captureRoboImage("build/screenshots/35_chat_scroll_date.png")
    }
}
