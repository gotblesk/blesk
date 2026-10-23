package app.blesk.feature.chats.groups

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The app draws under the keyboard: a sheet with a field rises above it, or the field and its button are typed blind. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w400dp-h860dp")
class SheetInsetsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val room = GroupState(
        id = "r",
        kind = GroupKind.Room,
        title = "Студия",
        version = 1,
        epoch = 0,
        members = listOf(GroupMember("eva", GroupRole.Owner)),
        channels = listOf(GroupChannel("c", "общий")),
    )

    @Test
    fun aNewChannelSheetRisesAboveTheKeyboard() {
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        compose.setContent {
            BleskTheme {
                RoomChannelSheets(room, "eva", GroupActions({}, { _, _, _ -> true }, { true }, { true }, {}), ChannelTarget.Add) {}
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("новости")
        val keyboard = (KEYBOARD_DP * compose.activity.resources.displayMetrics.density).toInt()
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
        }
        compose.waitForIdle()

        val top = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom - keyboard
        val create = compose.onNodeWithText("Создать").fetchSemanticsNode().boundsInRoot
        assertTrue("$create over the keyboard at $top", create.bottom <= top)
    }

    private companion object {
        const val KEYBOARD_DP = 300
    }
}
