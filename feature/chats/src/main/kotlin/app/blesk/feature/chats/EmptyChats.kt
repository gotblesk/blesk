package app.blesk.feature.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Text
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.motion.rememberLoopPhase
import app.blesk.core.design.theme.Blesk
import kotlin.math.PI
import kotlin.math.sin

/** An empty list is shapes and type, not a picture: two drops slowly drifting apart and together. */
@Composable
fun EmptyChats(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val phase by rememberLoopPhase(5200)
    Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier
                    .size(160.dp, 90.dp)
                    .metaballs(Blesk.colors.surfaceStrong, smoothness = 40.dp) {
                        val drift = (sin(phase * 2f * PI).toFloat() + 1f) / 2f
                        val r = size.height * 0.32f
                        val gap = r * (0.4f + 1.4f * drift)
                        val cy = size.height / 2f
                        listOf(
                            Blob(Rect(size.width / 2f - gap - r * 2, cy - r, size.width / 2f - gap, cy + r), r),
                            Blob(
                                Rect(size.width / 2f + gap * 0.3f, cy - r * 0.7f, size.width / 2f + gap * 0.3f + r * 1.4f, cy + r * 0.7f),
                                r,
                            ),
                        )
                    },
            )
            Text(stringResource(R.string.chats_empty_title), style = Blesk.type.title)
            Text(
                text = stringResource(R.string.chats_empty_text),
                color = Blesk.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }
    }
}
