package app.blesk.smoke

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.MainActivity
import app.blesk.screenshots.FrozenApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A channel's menu opened from the room list lies over the tab bar, whose taps would otherwise go first. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi", application = FrozenApplication::class)
class RoomListSheetsTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun aChannelMenuFromTheRoomListClosesWithCancel() {
        compose.onNodeWithContentDescription("Комнаты").performClick()
        compose.onNodeWithText("Студия").performClick()
        compose.onNode(hasText("общее") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            .performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithText("Изменить").assertIsDisplayed()

        // Near its start, where «Отмена» lies over the first tab: the tap is the sheet's, and the rooms stay.
        compose.onNodeWithText("Отмена").performTouchInput { click(Offset(left + EDGE_PX, centerY)) }
        compose.onNodeWithText("Изменить").assertDoesNotExist()
        compose.onNodeWithText("Студия").assertIsDisplayed()
    }

    private companion object {
        const val EDGE_PX = 40f
    }
}
