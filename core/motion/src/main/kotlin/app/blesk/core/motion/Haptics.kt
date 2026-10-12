package app.blesk.core.motion

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/** The «Вибрация» setting. */
enum class HapticLevel { Off, Quiet, Normal }

/** Set once at the top of the app from the settings. */
val LocalHapticLevel = staticCompositionLocalOf { HapticLevel.Normal }

/**
 * The vocabulary of touch. Screens ask for a meaning, never for a waveform, so one meaning
 * feels the same everywhere. Lighter meanings are for more frequent actions.
 *
 * Every pattern respects the system touch feedback setting and [HapticLevel]. Rich patterns
 * use [VibrationEffect.Composition] where the phone supports the primitives; otherwise the
 * closest predefined view feedback, and phones without amplitude control get plain clicks.
 */
@Stable
class Haptics(private val view: View, private val level: HapticLevel) {
    private val vibrator: Vibrator? = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val scale: Float get() = if (level == HapticLevel.Quiet) QUIET else 1f

    /** Light and short: a tab, a switch of microphone and camera, a gesture crossing its threshold, slider steps. */
    fun tick() = play(listOf(Primitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f))) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
    }

    /** A soft low tick: capsules merged, a reaction stuck, a chat pinned, a recording locked. */
    fun stick() = play(listOf(Primitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.9f))) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
    }

    /** A crisp click: a bridge snapped, a message sent, a circle video let go into the chat. */
    fun detach() = play(listOf(Primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f))) {
        HapticFeedbackConstants.CONTEXT_CLICK
    }

    /** A rising double pulse: keys verified, call connected, backup ready, profile saved. */
    fun success() = play(
        listOf(
            Primitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.45f),
            Primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, PAIR_GAP),
        ),
    ) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY }

    /** Two short dense pulses: not sent after every retry, wrong passcode, not allowed, name taken. */
    fun refuse() = play(
        listOf(
            Primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f),
            Primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, PAIR_GAP),
        ),
    ) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS }

    /** One heavy pulse: a contact's key changed, no microphone or camera, a danger slider opened. */
    fun warn() = play(listOf(Primitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f))) {
        HapticFeedbackConstants.LONG_PRESS
    }

    /** A heavy blow: someone blocked, a danger slider pulled to the end. */
    fun heavy() = play(
        listOf(
            Primitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f),
            Primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 0),
        ),
    ) { HapticFeedbackConstants.LONG_PRESS }

    /** The context click of a menu opened by a long press. */
    fun menu() {
        if (level != HapticLevel.Off) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Different clicks for on and off. */
    fun toggle(on: Boolean) {
        if (level == HapticLevel.Off) return
        val constant = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            else -> if (on) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }

    /** A short low fall at the end of a call. */
    fun callEnded() = play(listOf(Primitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.6f))) {
        HapticFeedbackConstants.CLOCK_TICK
    }

    private class Primitive(val id: Int, val scale: Float, val delay: Int = 0)

    private fun play(parts: List<Primitive>, fallback: () -> Int) {
        if (level == HapticLevel.Off) return
        val vibrator = vibrator?.takeIf { it.hasVibrator() && systemTouchEnabled() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator != null && parts.all { vibrator.supports(it.id) }) {
            val composition = VibrationEffect.startComposition()
            parts.forEach { composition.addPrimitive(it.id, (it.scale * scale).coerceIn(0f, 1f), it.delay) }
            vibrate(vibrator, composition.compose())
        } else {
            view.performHapticFeedback(fallback())
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun Vibrator.supports(primitive: Int): Boolean = areAllPrimitivesSupported(primitive)

    // From Android 13 the touch usage makes the system honour its own setting; before that we ask.
    private fun systemTouchEnabled(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
            Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private fun vibrate(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect)
        }
    }

    companion object {
        /** The incoming call pattern, in the rhythm of the light going round the avatar: wait, buzz, pause. */
        val RingPattern = longArrayOf(0, 120, 180, 120, 1100)

        private const val QUIET = 0.45f
        private const val PAIR_GAP = 70
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val level = LocalHapticLevel.current
    return remember(view, level) { Haptics(view, level) }
}
