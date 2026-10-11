package app.blesk.core.design.metaball

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp

/**
 * Draws the smooth union of [blobs] behind the content in [color]. Drops closer than
 * [smoothness] merge. [blobs] is read in the draw phase, so animating it redraws
 * without recomposition.
 */
@Composable
fun Modifier.metaballs(color: Color, smoothness: Dp, blobs: DrawScope.() -> List<Blob>): Modifier {
    val renderer = remember { MetaballRenderer() }
    return drawBehind { renderer.draw(this, blobs(), smoothness.toPx(), color) }
}

internal class MetaballRenderer {
    private val shader: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) MetaballShader() else null

    fun draw(scope: DrawScope, blobs: List<Blob>, smoothness: Float, color: Color) {
        if (blobs.isEmpty() || color.alpha == 0f) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && blobs.size <= MetaballShader.MAX_BLOBS) {
            val runtime = (shader as MetaballShader).update(blobs, smoothness, color)
            scope.drawRect(ShaderBrush(runtime))
        } else {
            scope.drawPath(MetaballPath.build(blobs, smoothness), color)
        }
    }
}
