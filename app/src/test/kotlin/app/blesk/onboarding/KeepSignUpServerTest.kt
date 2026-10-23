package app.blesk.onboarding

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.BleskApplication
import app.blesk.core.network.ServerAddress
import app.blesk.screenshots.FrozenApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The server chosen on «Другой сервер» is still the one after Android has closed the app in the background. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = FrozenApplication::class)
class KeepSignUpServerTest {
    @get:Rule
    val compose = createComposeRule()

    private val container = ApplicationProvider.getApplicationContext<BleskApplication>().container

    /** One life of the app: its sign-up, and the screens' state it was brought back with. */
    private class Life(val signUp: SignUp, saved: Map<String, List<Any?>>?) {
        val registry = SaveableStateRegistry(saved) { true }
    }

    @Test
    fun theChosenServerOutlivesTheApp() {
        val first = Life(SignUp(container), saved = null)
        var life by mutableStateOf<Life?>(first)
        compose.setContent {
            life?.let { now ->
                CompositionLocalProvider(LocalSaveableStateRegistry provides now.registry) { KeepSignUpServer(now.signUp) }
            }
        }
        val chosen = ServerAddress("https://chat.example.ru", "sha256/" + "A".repeat(PIN_LENGTH) + "=")
        first.signUp.server = chosen
        compose.waitForIdle()

        val saved = compose.runOnIdle { first.registry.performSave() }
        life = null
        compose.waitForIdle()
        val next = SignUp(container)
        life = Life(next, saved)
        compose.waitForIdle()

        assertEquals(chosen, next.server)
        assertEquals("chat.example.ru", next.otherServer)
    }

    private companion object {
        const val PIN_LENGTH = 43
    }
}
