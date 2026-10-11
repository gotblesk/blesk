package app.blesk.core.design.spark

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The single light of the app. [direction] is where the light comes from, as an offset in
 * -1..1 on both axes ((0, -1) is straight above). Glints read it in the draw phase only, so
 * tilting the phone redraws them without recomposing anything.
 */
@Stable
class LightSource internal constructor(initial: Offset) {
    private val state = mutableStateOf(initial)

    val direction: State<Offset> get() = state

    internal fun moveTowards(target: Offset) {
        val current = state.value
        state.value = current + (target - current) * SMOOTHING
    }

    companion object {
        /** Light from the top, slightly from the left: where a ceiling lamp usually is. */
        val Rest = Offset(-0.25f, -1f)
        private const val SMOOTHING = 0.18f

        fun still() = LightSource(Rest)
    }
}

val LocalLightSource = staticCompositionLocalOf { LightSource.still() }

/**
 * Follows the gravity sensor while the screen is resumed. Stays still when [enabled] is false
 * (animations off or tilt response disabled) and while battery saver is on.
 */
@Composable
fun rememberLightSource(enabled: Boolean): LightSource {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val source = remember { LightSource.still() }
    DisposableEffect(enabled, context, lifecycle) {
        if (!enabled) return@DisposableEffect onDispose { }
        val tracker = TiltTracker(context, source)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> tracker.start()
                Lifecycle.Event.ON_PAUSE -> tracker.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            tracker.stop()
        }
    }
    return source
}

private class TiltTracker(context: Context, private val source: LightSource) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val gravity = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var listening = false

    fun start() {
        if (listening || gravity == null || power?.isPowerSaveMode == true) return
        listening = sensors?.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI) == true
    }

    fun stop() {
        if (!listening) return
        sensors?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = (event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val y = (event.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        // Phone held upright: gravity mostly along +y. Tilting shifts the light a little, never fully.
        val target = Offset(
            LightSource.Rest.x + x * TILT_REACH,
            LightSource.Rest.y + (1f - y) * TILT_REACH,
        )
        source.moveTowards(target)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val TILT_REACH = 0.45f
    }
}
