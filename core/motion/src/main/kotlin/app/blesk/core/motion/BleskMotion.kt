package app.blesk.core.motion

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** How long things take. Screens never write these numbers themselves. */
object Durations {
    /** A press answering a finger. */
    const val PRESS = 140

    /** An ordinary change of place or screen. */
    const val TRANSITION = 280

    /** One of the signature animations: sending, key verification, a tab handing over. */
    const val SIGNATURE = 420

    /** The spectral flash of an event. */
    const val FIRE = 380

    /** What every movement turns into when the system asks for no animations. */
    const val DISSOLVE = 90

    /** Followers trail the element under the finger by this much. */
    const val FOLLOW_DELAY = 25
}

/** The one curve for movement that is not a spring: a viscous start and a soft stop. */
val Viscous: Easing = CubicBezierEasing(0.45f, 0f, 0.15f, 1f)

/**
 * Motion of the whole app. Springs damp between 0.75 and 0.85, so nothing overshoots by more
 * than about five percent and nothing reads as a cartoon bounce.
 *
 * [reduced] follows the system «remove animations»: movement becomes a quick dissolve, and the
 * tilt light and metaball bridges stop. [still] also covers battery saver, where only the light
 * stops moving: transitions still run, because they explain what happened.
 */
@Immutable
class BleskMotion(val reduced: Boolean, val still: Boolean = reduced) {

    /** Something coming to rest where it belongs. */
    fun <T> settle(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /** A selection moving: tabs, segments, toggles. */
    fun <T> select(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)

    /** A metaball bridge stretching and snapping back. */
    fun <T> stretch(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)

    fun <T> press(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(Durations.PRESS, easing = Viscous)

    fun <T> transition(): FiniteAnimationSpec<T> = tween(if (reduced) Durations.DISSOLVE else Durations.TRANSITION, easing = Viscous)

    fun <T> signature(): FiniteAnimationSpec<T> = tween(if (reduced) Durations.DISSOLVE else Durations.SIGNATURE, easing = Viscous)

    companion object {
        val Default = BleskMotion(reduced = false)
    }
}

/** Reads the system animation scale and battery saver once per context. */
@Composable
fun rememberSystemMotion(): BleskMotion {
    val context = LocalContext.current
    return remember(context) { systemMotion(context) }
}

fun systemMotion(context: Context): BleskMotion {
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val saving = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    val reduced = scale == 0f
    return BleskMotion(reduced = reduced, still = reduced || saving)
}
