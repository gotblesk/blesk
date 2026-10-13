package app.blesk.debug

import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.blesk.R
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import kotlin.math.roundToInt

/**
 * «Индикатор кадров»: how many frames the window really drew in the last half second and how
 * many refreshes were missed since it was turned on. It counts frames the system reports as
 * drawn, so a still screen shows rest rather than a made-up number. Beside it, the rate the
 * screen refreshes at right now (phones drop from 120 to 60 Hz on their own) and whether power
 * saving is on, which cuts smoothness by itself.
 */
@Composable
internal fun FrameMeter(window: Window, modifier: Modifier = Modifier) {
    var reading by remember { mutableStateOf(Reading(fps = 0, dropped = 0, hertz = 0, saving = false)) }
    DisposableEffect(window) {
        val power = window.context.getSystemService(PowerManager::class.java)
        fun refresh() = window.decorView.display?.refreshRate?.takeIf { it > 0f } ?: DEFAULT_REFRESH
        var budget = (NANOS_PER_SECOND / refresh()).toLong()
        var frames = 0
        var dropped = 0
        var since = System.nanoTime()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames++
            val took = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            // A frame that took longer than one refresh made the screen show the previous one again.
            if (took > budget) dropped += ((took - 1) / budget).toInt()
            val now = System.nanoTime()
            if (now - since >= WINDOW_NANOS) {
                val fps = (frames * NANOS_PER_SECOND / (now - since)).roundToInt()
                // The capsule itself redraws twice a second; that is rest, not movement.
                val hertz = refresh()
                reading = Reading(
                    fps = if (frames <= OWN_FRAMES) 0 else fps,
                    dropped = dropped,
                    hertz = hertz.roundToInt(),
                    saving = power?.isPowerSaveMode == true,
                )
                budget = (NANOS_PER_SECOND / hertz).toLong()
                frames = 0
                since = now
            }
        }
        window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
        onDispose { window.removeOnFrameMetricsAvailableListener(listener) }
    }
    val frames = if (reading.fps == 0) {
        stringResource(R.string.frame_meter_rest, reading.dropped)
    } else {
        stringResource(R.string.frame_meter_reading, reading.fps, reading.dropped)
    }
    val screen = if (reading.hertz > 0) stringResource(R.string.frame_meter_hertz, reading.hertz) + " · " else ""
    val saving = if (reading.saving) " · " + stringResource(R.string.frame_meter_saving) else ""
    val text = screen + frames + saving
    Text(
        text = text,
        style = Blesk.type.caption.copy(fontFamily = FontFamily.Monospace),
        color = Blesk.colors.textPrimary,
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 4.dp)
            .background(Blesk.colors.surfaceStrong, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private class Reading(val fps: Int, val dropped: Int, val hertz: Int, val saving: Boolean)

private const val DEFAULT_REFRESH = 60f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val WINDOW_NANOS = 500_000_000L
private const val OWN_FRAMES = 2
