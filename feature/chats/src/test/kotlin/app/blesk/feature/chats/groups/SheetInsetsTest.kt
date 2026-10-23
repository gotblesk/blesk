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
        val top = raiseKeyboard(KEYBOARD_DP)

        val create = compose.onNodeWithText("Создать").fetchSemanticsNode().boundsInRoot
        assertTrue("$create over the keyboard at $top", create.bottom <= top)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2f)
    fun aChannelsEditSheetKeepsItsButtonWholeAtTwiceTheTextSize() {
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        compose.setContent {
            BleskTheme {
                RoomChannelSheets(room, "eva", GroupActions({}, { _, _, _ -> true }, { true }, { true }, {}), ChannelTarget.Edit("c")) {}
            }
        }
        // A small phone with the status bar and a keyboard: the fields give way, the button stays whole above it.
        val top = raiseKeyboard(SMALL_KEYBOARD_DP, statusBarDp = STATUS_BAR_DP)

        val save = compose.onNodeWithText("Сохранить").fetchSemanticsNode().boundsInRoot
        val whole = TOUCH_TARGET_DP * compose.activity.resources.displayMetrics.density
        assertTrue("$save whole over the keyboard at $top", save.bottom <= top && save.height >= whole)
    }

    /** Opens a keyboard [keyboardDp] tall under a status bar [statusBarDp] tall; returns where the keyboard begins. */
    private fun raiseKeyboard(keyboardDp: Int, statusBarDp: Int = 0): Float {
        val density = compose.activity.resources.displayMetrics.density
        val keyboard = (keyboardDp * density).toInt()
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (statusBarDp * density).toInt(), 0, 0))
                .build()
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
        }
        compose.waitForIdle()
        return compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom - keyboard
    }

    private companion object {
        const val KEYBOARD_DP = 300
        const val SMALL_KEYBOARD_DP = 260
        const val STATUS_BAR_DP = 24
        const val TOUCH_TARGET_DP = 48
    }
}
