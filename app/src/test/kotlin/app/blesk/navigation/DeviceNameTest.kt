package app.blesk.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class DeviceNameTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun knownCodesGetTheirNames() {
        assertEquals("Nothing Phone (3a) Pro", deviceName(context, model = "A059P", maker = "Nothing"))
        assertEquals("Nothing Phone (3a)", deviceName(context, model = "A059", maker = "Nothing"))
        assertEquals("Samsung Galaxy S23", deviceName(context, model = "SM-S911B", maker = "samsung"))
    }

    @Test
    fun unknownPhonesKeepMakerAndModel() {
        assertEquals("Pixel 8", deviceName(context, model = "Pixel 8", maker = "Google").removePrefix("Google "))
        assertEquals("Fairphone FP5", deviceName(context, model = "FP5", maker = "Fairphone"))
    }
}
