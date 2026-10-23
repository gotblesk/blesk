package app.blesk.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.settings.SetupStep
import app.blesk.core.data.settings.StartState
import app.blesk.core.network.ServerAddress
import app.blesk.navigation.HomeKey
import app.blesk.navigation.ServerKey
import app.blesk.navigation.StackDirection
import app.blesk.navigation.replaceWith
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The setup opens from the saved start state, not from whichever screen asked for the account. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SetupOpenerTest {
    @get:Rule
    val compose = createComposeRule()

    private val own = ServerRef.of(ServerAddress.DEFAULT)
    private val start = MutableStateFlow<StartState>(StartState.Demo)

    private fun open(vararg keys: NavKey): NavBackStack<NavKey> {
        val backStack = NavBackStack(*keys)
        val direction = StackDirection(backStack.size)
        compose.setContent { SetupOpener(start, backStack, direction) }
        compose.waitForIdle()
        return backStack
    }

    private fun becomes(state: StartState) {
        start.value = state
        compose.waitForIdle()
    }

    @Test
    fun theAccountSavedWithItsFirstStepOpensIt() {
        val backStack = open(HomeKey, ServerKey, InviteKey(own), UsernameKey(own, "abcdefghjk23"))
        assertEquals(4, backStack.size)

        becomes(StartState.Account(SetupStep.Profile))

        assertEquals(listOf<NavKey>(SetupKey(SetupStep.Profile)), backStack.toList())
    }

    @Test
    fun aStartWithAStepPendingTakesItUp() {
        start.value = StartState.Account(SetupStep.Theme)

        val backStack = open(HomeKey)

        assertEquals(listOf<NavKey>(SetupKey(SetupStep.Theme)), backStack.toList())
    }

    @Test
    fun theStepsAndTheEndOfTheSetupAreLeftToItsScreens() {
        start.value = StartState.Account(SetupStep.Profile)
        val backStack = open(SetupKey(SetupStep.Profile))
        backStack.add(SetupKey(SetupStep.Phrase))
        becomes(StartState.Account(SetupStep.Phrase))
        assertEquals(listOf<NavKey>(SetupKey(SetupStep.Profile), SetupKey(SetupStep.Phrase)), backStack.toList())

        // The chats are open before the last step is read back, and then before its clearing is.
        backStack.replaceWith(listOf(HomeKey), StackDirection(backStack.size))
        becomes(StartState.Account(SetupStep.Glint))
        becomes(StartState.Account(null))

        assertEquals(listOf<NavKey>(HomeKey), backStack.toList())
    }

    @Test
    fun withoutAnAccountThereIsNothingToSetUp() {
        val backStack = open(HomeKey, ServerKey)

        becomes(StartState.Welcome)
        becomes(StartState.Demo)

        assertEquals(listOf<NavKey>(HomeKey, ServerKey), backStack.toList())
    }
}
