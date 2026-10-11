package app.blesk.core.design.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The three touches of blesk: a soft tick when things stick together, a click when they come
 * apart, and a light double pulse for events. All respect the system haptics setting.
 */
@Stable
class Haptics internal constructor(private val view: View) {
    private val vibrator: Vibrator? = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun merge() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }

    fun detach() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun event() {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, PULSE_SCALE)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, PULSE_SCALE, PULSE_GAP_MILLIS)
                .compose()
            vibrate(vibrator, effect)
        } else if (!view.performHapticFeedback(confirmConstant())) {
            vibrate(vibrator, VibrationEffect.createWaveform(longArrayOf(0, 10, 70, 10), -1))
        }
    }

    private fun confirmConstant() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP

    private fun vibrate(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect)
        }
    }

    private companion object {
        const val PULSE_SCALE = 0.6f
        const val PULSE_GAP_MILLIS = 60
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
