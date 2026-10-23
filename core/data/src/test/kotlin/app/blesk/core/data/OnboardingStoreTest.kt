package app.blesk.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.AutoDownload
import app.blesk.core.data.settings.AutoKind
import app.blesk.core.data.settings.GestureHint
import app.blesk.core.data.settings.LastServer
import app.blesk.core.data.settings.NotificationPreview
import app.blesk.core.data.settings.OnboardingStore
import app.blesk.core.data.settings.Profile
import app.blesk.core.data.settings.ProxySetting
import app.blesk.core.data.settings.SettingsStore
import app.blesk.core.data.settings.SetupStep
import app.blesk.core.data.settings.StartState
import app.blesk.core.data.settings.VibrationLevel
import app.blesk.core.data.settings.appMode
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Each test has a settings file of its own: the app's one is shared by every test in the process. */
class OnboardingStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file by lazy { WriteLog(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") }) }
    private val settings by lazy { SettingsStore(file) }
    private val onboarding by lazy { OnboardingStore(settings) }

    @After
    fun closeTheFile() {
        scope.cancel()
    }

    @Test
    fun signingUpDropsExactlyTheDemoKeys() = runBlocking {
        everythingSet()
        val before = settings.store.data.first().asMap()

        onboarding.enterAccount(EVA, keepNameAndPhoto = true, setup = null)

        val after = settings.store.data.first().asMap()
        assertEquals(DEMO_KEYS, (before.keys - after.keys).map { it.name }.toSet())
        assertEquals(before.filterKeys { it in after.keys }, after)
        val profile = settings.profile.first()
        assertEquals("Ева", profile.name)
        assertEquals(1L, profile.photoVersion)
        assertEquals("", profile.bio)
        assertEquals(emptyList<String>(), profile.links)
        assertEquals(Profile.DEFAULT_HEADER, profile.header)
        assertEquals(0L, profile.headerVersion)
        assertEquals(emptyMap<String, ThemeSpec>(), settings.chatThemes.first())
        assertEquals(emptySet<String>(), settings.notifications.first().mentionsOnly)
    }

    @Test
    fun aPhraseLoginAlsoDropsTheDemoNameAndPhoto() = runBlocking {
        everythingSet()
        val before = settings.store.data.first().asMap()

        onboarding.enterAccount(EVA, keepNameAndPhoto = false, setup = null)

        val after = settings.store.data.first().asMap()
        assertEquals(DEMO_KEYS + setOf("profile_name", "profile_photo"), (before.keys - after.keys).map { it.name }.toSet())
        assertEquals(before.filterKeys { it in after.keys }, after)
        val profile = settings.profile.first()
        assertEquals("", profile.name)
        assertEquals(0L, profile.photoVersion)
    }

    @Test
    fun theAccountIsSavedInTheWriteThatEndsTheDemo() = runBlocking {
        onboarding.chooseDemo()
        settings.setChatTheme("anya", ThemeSpec.Lilac)
        val before = file.writes.size

        onboarding.enterAccount(EVA, keepNameAndPhoto = true, setup = SetupStep.Profile)

        // One write: a phone stopped on the way never has the account under the demo's settings, nor the demo
        // given up with no account yet, which would open the welcome screen, nor the account without its setup.
        val written = file.writes.drop(before).single()
        assertEquals(EVA, written.appMode())
        assertEquals(emptyList<String>(), written.asMap().keys.map { it.name }.filter { it in DEMO_KEYS })
        assertEquals(SetupStep.Profile.name, written[stringPreferencesKey("setup_step")])
        assertEquals(StartState.Account(SetupStep.Profile), onboarding.start.first())
    }

    @Test
    fun anAccountIsSavedWithTheChannelItsServerNamed() = runBlocking {
        onboarding.setOfficialChannel("old_news")
        onboarding.finishSetup(subscribeTo = "old_news")
        val before = file.writes.size

        onboarding.enterAccount(EVA, keepNameAndPhoto = true, setup = SetupStep.Profile, officialChannel = "blesk")

        assertEquals(1, file.writes.size - before)
        assertEquals("blesk", onboarding.officialChannel.first())
        // A subscription to the server before is not made on this one.
        assertNull(onboarding.subscribePending.first())

        onboarding.enterAccount(EVA, keepNameAndPhoto = true, setup = SetupStep.Profile, officialChannel = null)
        assertNull(onboarding.officialChannel.first())
    }

    @Test
    fun theSetupEndsWithItsSubscriptionInOneWrite() = runBlocking {
        onboarding.enterAccount(EVA, keepNameAndPhoto = true, setup = SetupStep.Glint, officialChannel = "blesk")
        val before = file.writes.size

        onboarding.finishSetup(subscribeTo = "blesk")

        // A phone stopped right after the setup still subscribes at its next start.
        val written = file.writes.drop(before).single()
        assertNull(written[stringPreferencesKey("setup_step")])
        assertEquals("blesk", written[stringPreferencesKey("subscribe_pending")])
        assertEquals(StartState.Account(null), onboarding.start.first())
    }

    @Test
    fun aSubscriptionLeftUntickedIsNotMade() = runBlocking {
        onboarding.enterAccount(EVA, keepNameAndPhoto = true, setup = SetupStep.Glint, officialChannel = "blesk")

        onboarding.finishSetup(subscribeTo = null)

        assertNull(onboarding.subscribePending.first())
        assertEquals(StartState.Account(null), onboarding.start.first())
    }

    @Test
    fun onlyTheSubscriptionTakenIsCrossedOff() = runBlocking {
        onboarding.finishSetup(subscribeTo = "news")

        onboarding.subscribed("blesk")
        assertEquals("news", onboarding.subscribePending.first())

        onboarding.subscribed("news")
        assertNull(onboarding.subscribePending.first())
    }

    @Test
    fun notificationsAreAskedAboutOnce() = runBlocking {
        assertFalse(onboarding.notificationsAsked.first())

        onboarding.markNotificationsAsked()

        assertTrue(onboarding.notificationsAsked.first())
    }

    @Test
    fun aNameOfOnlyInvisibleCharactersIsNotSaved() = runBlocking {
        assertTrue(onboarding.setProfileName("Аня"))

        assertFalse(onboarding.setProfileName("​‮ㅤ"))
        assertEquals("Аня", settings.profile.first().name)

        assertTrue(onboarding.setProfileName("  Ева‮ "))
        assertEquals("Ева", settings.profile.first().name)

        assertTrue(onboarding.setProfileName(""))
        assertEquals("", settings.profile.first().name)
    }

    @Test
    fun theLastServerIsRemembered() = runBlocking {
        assertNull(onboarding.lastServer.first())

        onboarding.rememberServer("https://chat.example", "sha256/k7m2p9xq")
        assertEquals(LastServer("https://chat.example", "sha256/k7m2p9xq"), onboarding.lastServer.first())

        onboarding.rememberServer("https://other.example", null)
        assertEquals(LastServer("https://other.example", null), onboarding.lastServer.first())
    }

    @Test
    fun theSetupStepResumesUntilCleared() = runBlocking {
        assertEquals(StartState.Welcome, onboarding.start.first())
        onboarding.chooseDemo()
        assertEquals(StartState.Demo, onboarding.start.first())

        settings.setServer(AppMode.Server(url = "https://api.blesk.su", pin = null, username = "anya"))
        assertEquals(StartState.Account(null), onboarding.start.first())
        onboarding.setSetupStep(SetupStep.Code)
        assertEquals(StartState.Account(SetupStep.Code), onboarding.start.first())
        onboarding.setSetupStep(null)
        assertEquals(StartState.Account(null), onboarding.start.first())

        settings.store.edit { it[stringPreferencesKey("setup_step")] = "Backup" }
        assertEquals(StartState.Account(null), onboarding.start.first())
    }

    /** Every preference a person can have in the demo, none at its default. */
    private suspend fun everythingSet() {
        settings.updateAppearance { it.copy(theme = ThemeSpec.Lime, textScale = 1.2f) }
        settings.setChatTheme("anya", ThemeSpec.Lilac)
        settings.bumpWallpaper()
        settings.updateNotifications { it.copy(preview = NotificationPreview.Nothing, mentionsOnly = setOf("room:general")) }
        settings.updateProfile(name = "Ева", username = "eva.demo", bio = "Люблю горы", links = listOf("https://blesk.su"))
        settings.setProfileHeader("solid:3")
        settings.bumpProfileHeader()
        settings.bumpProfilePhoto()
        settings.updatePrivacy { it.copy(biometric = true, findable = false) }
        settings.setVibration(VibrationLevel.Quiet)
        settings.setProxy(ProxySetting(enabled = true, socks = false, host = "proxy.example", port = 3128))
        settings.markHint(GestureHint.Reply)
        settings.setDoubleTapReaction("★")
        settings.setChatBackground("solid:2")
        settings.setKeepMediaDays(30)
        settings.setAutoDownload(AutoDownload(wifi = setOf(AutoKind.Photos), mobile = emptySet()))
        settings.setCheckUpdates(false)
        settings.setComposer(sendByEnter = true, circle = true, tapToRecord = true)
        settings.setKeepArchived(true)
        settings.setArchiveFolded(true)
        settings.pickEmoji("★")
        settings.setDebugUnlocked(true)
        onboarding.chooseDemo()
        onboarding.rememberServer("https://chat.example", null)
        settings.setServer(EVA)
    }

    /** Keeps what each write left in the file. */
    private class WriteLog(private val store: DataStore<Preferences>) : DataStore<Preferences> by store {
        val writes = CopyOnWriteArrayList<Preferences>()

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            store.updateData(transform).also { writes += it }
    }

    private companion object {
        val EVA = AppMode.Server(url = "https://chat.example", pin = null, username = "eva")
        val DEMO_KEYS = setOf(
            "chat_themes",
            "mentions_only",
            "profile_username",
            "profile_bio",
            "profile_links",
            "profile_header",
            "profile_header_photo",
            "onboarding_demo",
        )
    }
}
