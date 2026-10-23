package app.blesk.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.settings.KeptOffline
import app.blesk.core.data.settings.OfflineKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class KeptOfflineTest {
    private val kept = KeptOffline(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun clearingForgetsEverything() = runBlocking {
        val keys = setOf(OfflineKey("anya", "m1"), OfflineKey("room:general", "m2"))
        keys.forEach { kept.set(it, kept = true) }
        assertEquals(keys, kept.keys.first() intersect keys)

        kept.clear()

        assertEquals(emptySet<OfflineKey>(), kept.keys.first())
    }
}
