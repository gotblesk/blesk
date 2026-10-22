package app.blesk.screenshots

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import app.blesk.BleskApplication
import app.blesk.core.design.theme.LocalClock
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import java.util.TimeZone
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The world every screenshot is taken in. The same screen has to come out pixel for pixel the
 * same on any day, at any hour and in any time zone, so release screenshots can be compared.
 */
internal object Frozen {
    val moment: Instant = Instant.parse("2026-10-11T15:00:00Z")
    private val zone: TimeZone = TimeZone.getTimeZone("Europe/Moscow")

    /**
     * Fixes the time zone and makes Coil decode on the calling thread. Its default loader decodes
     * on background threads, and a capture can come before a picture or a sticker is ready.
     */
    fun prepare(context: Context) {
        TimeZone.setDefault(zone)
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).coroutineContext(EmptyCoroutineContext).build())
    }
}

/** Captures [content] into [path] at [Frozen.moment], with every image already in place. */
internal fun captureFrozen(path: String, content: @Composable () -> Unit) {
    Frozen.prepare(ApplicationProvider.getApplicationContext())
    captureRoboImage(path) {
        CompositionLocalProvider(LocalClock provides { Frozen.moment }) { content() }
    }
}

/** The whole app, as the smoke tests drive it, with its clock stopped at [Frozen.moment]. */
class FrozenApplication : BleskApplication() {
    override val clock: () -> Instant = { Frozen.moment }

    override fun onCreate() {
        Frozen.prepare(this)
        super.onCreate()
    }
}
