package app.blesk.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SettingsProfileTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun onAServerTheProfileCarriesTheAccountUsername() = runBlocking {
        val settings = SettingsStore(context)
        settings.updateProfile(name = "Аня", username = "anya_demo", bio = "")
        assertEquals("anya_demo", settings.profile.first().username)

        settings.setServer(AppMode.Server(url = "https://api.blesk.su", pin = null, username = "anya"))

        val profile = settings.profile.first()
        assertEquals("anya", profile.username)
        assertEquals("Аня", profile.name)
    }
}
