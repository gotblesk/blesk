package app.blesk.core.design.metaball

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color

/**
 * Signed distance field of rounded boxes joined with a polynomial smooth minimum. Where two
 * drops are closer than [k] they flow into one; further apart they stay two. Edges are
 * anti-aliased on the distance itself, so there is no blur-and-threshold raggedness.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class MetaballShader {
    private val shader = RuntimeShader(SOURCE)
    private val rects = FloatArray(MAX_BLOBS * 4)
    private val radii = FloatArray(MAX_BLOBS)

    fun update(blobs: List<Blob>, smoothness: Float, color: Color): RuntimeShader {
        val count = minOf(blobs.size, MAX_BLOBS)
        rects.fill(0f)
        radii.fill(0f)
        for (i in 0 until count) {
            val blob = blobs[i]
            rects[i * 4] = blob.rect.left
            rects[i * 4 + 1] = blob.rect.top
            rects[i * 4 + 2] = blob.rect.right
            rects[i * 4 + 3] = blob.rect.bottom
            radii[i] = blob.clampedRadius
        }
        shader.setFloatUniform("rects", rects)
        shader.setFloatUniform("radii", radii)
        shader.setFloatUniform("count", count.toFloat())
        shader.setFloatUniform("k", smoothness.coerceAtLeast(MIN_SMOOTHNESS))
        val a = color.alpha
        shader.setFloatUniform("color", color.red * a, color.green * a, color.blue * a, a)
        return shader
    }

    companion object {
        const val MAX_BLOBS = 32
        private const val MIN_SMOOTHNESS = 0.01f

        private val SOURCE = """
            uniform float4 rects[$MAX_BLOBS];
            uniform float radii[$MAX_BLOBS];
            uniform float count;
            uniform float k;
            uniform float4 color;

            float roundBox(float2 p, float4 r, float radius) {
                float2 center = (r.xy + r.zw) * 0.5;
                float2 extent = (r.zw - r.xy) * 0.5;
                float2 q = abs(p - center) - extent + radius;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }

            float smin(float a, float b, float k) {
                float h = max(k - abs(a - b), 0.0) / k;
                return min(a, b) - h * h * k * 0.25;
            }

            half4 main(float2 p) {
                float d = 100000.0;
                for (int i = 0; i < $MAX_BLOBS; i++) {
                    if (float(i) >= count) break;
                    d = smin(d, roundBox(p, rects[i], radii[i]), k);
                }
                float coverage = clamp(0.5 - d, 0.0, 1.0);
                return half4(color * coverage);
            }
        """.trimIndent()
    }
}
