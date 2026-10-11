package app.blesk.core.design.motion

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Springs with a purpose: [settle] for things coming to rest, [select] for selection, [stretch]
 * for metaball bridges. None overshoots enough to read as a bounce.
 * When the system animation scale is zero every spec collapses to an instant change.
 */
@Immutable
class BleskMotion(val reduced: Boolean) {

    fun <T> settle(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMediumLow)

    fun <T> select(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)

    fun <T> stretch(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)

    companion object {
        val Default = BleskMotion(reduced = false)
    }
}

@Composable
fun rememberSystemMotion(): BleskMotion {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        BleskMotion(reduced = scale == 0f)
    }
}
