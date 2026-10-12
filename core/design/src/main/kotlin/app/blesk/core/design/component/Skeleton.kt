package app.blesk.core.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blesk.core.design.R
import app.blesk.core.design.motion.rememberLoopPhase
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.delay

/**
 * Placeholder rows shaped like the list that is coming: an avatar and two lines each. They are
 * the surface colour of the theme, and instead of the usual shimmer a slow light passes over
 * them; with animations off they stand still. Nothing is shown for the first 300 ms, so a quick
 * load never flashes a skeleton, and the real content dissolves in on the same place.
 */
@Composable
fun SkeletonRows(count: Int, modifier: Modifier = Modifier, avatar: Dp = 44.dp) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SHOW_AFTER_MILLIS)
        visible = true
    }
    val label = stringResource(R.string.design_loading)
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn(Blesk.motion.transition()), exit = fadeOut()) {
        val phase by rememberLoopPhase(LIGHT_PERIOD_MILLIS)
        val surface = Blesk.colors.surface
        val light = Blesk.colors.light.copy(alpha = if (Blesk.colors.isDark) 0.08f else 0.35f)
        val still = Blesk.motion.still
        Column(
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label }
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        if (still) return@onDrawWithContent
                        val band = size.width * 0.6f
                        val x = -band + (size.width + band * 2) * phase
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, light, Color.Transparent),
                                start = Offset(x - band / 2, 0f),
                                end = Offset(x + band / 2, size.height),
                            ),
                        )
                    }
                },
        ) {
            repeat(count) { index ->
                Row(
                    Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(avatar).clip(CircleShape).background(surface))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Bar(widthFraction = if (index % 2 == 0) 0.45f else 0.6f, color = surface)
                        Bar(widthFraction = if (index % 2 == 0) 0.8f else 0.7f, color = surface)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(widthFraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
    )
}

private const val SHOW_AFTER_MILLIS = 300L
private const val LIGHT_PERIOD_MILLIS = 2_400
