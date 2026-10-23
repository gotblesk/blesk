package app.blesk

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.settings.SettingsStore
import app.blesk.screenshots.FrozenApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What a test's app chooses stays in that test: the app's own settings store outlives it in the process. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = FrozenApplication::class)
class TestSettingsTest {
    private val application = ApplicationProvider.getApplicationContext<BleskApplication>()

    @Test
    fun aTestsAppKeepsItsSettingsToItself() {
        runBlocking {
            application.container.settings.setDebugUnlocked(true)

            assertTrue(application.container.settings.debugUnlocked.first())
            assertFalse(SettingsStore(application).debugUnlocked.first())
        }
    }
}
