package app.blesk

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.demo.DemoRepository
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.attachmentLabel
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.OfflineKey
import app.blesk.core.data.settings.SettingsStore
import app.blesk.core.data.settings.SetupStep
import app.blesk.core.data.settings.StartState
import app.blesk.core.network.BleskApi
import app.blesk.core.network.ServerAddress
import app.blesk.screenshots.FrozenApplication
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * The switch from the demo to a new account. A stand-in takes the server repository's place: that one opens
 * the encrypted database, and SQLCipher does not load under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = FrozenApplication::class)
class AccountSwitchTest {
    private val application = ApplicationProvider.getApplicationContext<BleskApplication>()

    // A settings file of its own, so the account saved here never reaches the next test's app.
    private val container =
        AppContainer(application, settings = SettingsStore(application, "switch-${UUID.randomUUID()}"))
    private val watching = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun stop() {
        watching.cancel()
        container.scope.cancel()
    }

    @Test
    fun theSwitchRunsToItsEndWhenTheScreenThatAskedIsGone() {
        until { container.chats.value != null }
        val header = File(application.filesDir, "header.jpg").apply { writeText("demo") }
        val recording = File(application.filesDir, "media/demo/voice.ogg").apply {
            parentFile?.mkdirs()
            writeText("demo")
        }
        runBlocking {
            container.chatLocks.setLocked(DEMO_CHAT, true)
            container.keptOffline.set(OfflineKey(DEMO_CHAT, "m1"), kept = true)
            container.settings.setChatTheme(DEMO_CHAT, ThemeSpec.Lime)
            container.onboarding.chooseDemo()
        }
        container.pendingLock.value = DEMO_CHAT

        // What of the demo was still there the moment the account was saved: a phone stopped then would keep it.
        var locked = true
        var kept = emptySet<OfflineKey>()
        var left: List<Any?>? = null
        watching.launch { container.chatLocks.locks.collect { locked = it.isLocked(DEMO_CHAT) } }
        watching.launch { container.keptOffline.keys.collect { kept = it } }
        watching.launch {
            container.settings.mode.first { it is AppMode.Server }
            left = listOf(locked, kept, header.exists(), container.pendingLock.value)
        }

        val build = CompletableDeferred<Unit>()
        val account = standIn()
        val asked = container.scope.launch {
            container.switchTo(ACCOUNT, AppContainer.Entry.SignUp, BleskApi(ServerAddress(ACCOUNT.url))) {
                build.await()
                account
            }
        }
        until { left != null }
        // The account is saved, so the form that asked leaves the screen and takes its work with it.
        asked.cancel()
        build.complete(Unit)
        until { container.chats.value === account && !recording.exists() }

        assertEquals(listOf(false, emptySet<OfflineKey>(), false, null), left)
        assertSame(account, container.chats.value)
        assertFalse(recording.exists())
        assertTrue(container.eventLog.entries.value.any { it.code == EventCode.ServerRegistered })
        runBlocking {
            assertEquals(StartState.Account(SetupStep.Profile), container.onboarding.start.first())
            assertNull(container.settings.chatThemes.first()[DEMO_CHAT])
        }
    }

    private fun standIn(): ChatRepository = DemoRepository(
        DemoSeed.from(application.resources, Instant.now()),
        application.resources::attachmentLabel,
        container.scope,
        EventLog(),
        Instant::now,
    )

    /** The container works on the main thread and writes files off it; this turns both until [done] or a timeout. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(STEP_MILLIS)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        const val DEMO_CHAT = "anya"
        val ACCOUNT = AppMode.Server(url = "https://chat.example.ru", pin = null, username = "egor")
        const val WAIT_MILLIS = 10_000L
        const val STEP_MILLIS = 10L
    }
}
