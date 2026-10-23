package app.blesk

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.demo.DemoRepository
import app.blesk.core.data.settings.OnboardingStore
import app.blesk.core.data.settings.SettingsStore
import app.blesk.screenshots.FrozenApplication
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = FrozenApplication::class)
class DemoMediaTest {
    private val application = ApplicationProvider.getApplicationContext<BleskApplication>()

    @Test
    fun aChosenDemoStartsWithoutTheLastOnesFilesAndRecordsApart() {
        val media = File(application.filesDir, "media")
        val leftover = File(media, "demo/voice.ogg").apply { parentFile?.mkdirs(); writeText("demo") }
        val received = File(media, "photo.jpg").apply { writeText("account") }
        // A settings file of its own, where the demo was chosen at an earlier start.
        val settings = SettingsStore(application, "demo-${UUID.randomUUID()}")
        runBlocking { OnboardingStore(settings).chooseDemo() }
        val container = AppContainer(application, settings = settings)
        try {
            shown(container)

            assertTrue(container.chats.value is DemoRepository)
            assertFalse(leftover.exists())
            assertTrue(received.exists())
            assertEquals(File(media, "demo"), container.media.newFile("ogg").parentFile)
        } finally {
            container.scope.cancel()
            received.delete()
        }
    }

    /** The container reads its settings and clears files off the main thread, then shows the world on it. */
    private fun shown(container: AppContainer) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (container.chats.value == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(STEP_MILLIS)
        }
    }

    private companion object {
        const val WAIT_MILLIS = 10_000L
        const val STEP_MILLIS = 10L
    }
}
