package app.blesk.core.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.R
import app.blesk.core.design.glass.lens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk

/**
 * A short gesture hint in a small glass capsule, one at a time. It stays until the gesture is
 * done or the capsule is closed; [text] null hides it.
 */
@Composable
fun HintCapsule(text: String?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(text) }
    if (text != null) shown = text
    val motion = Blesk.motion
    AnimatedVisibility(
        visible = text != null,
        enter = scaleIn(motion.settle(), initialScale = 0.8f) + fadeIn(motion.transition()),
        exit = fadeOut(motion.transition()),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .lens(RoundedCornerShape(24.dp), elevation = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(shown.orEmpty(), style = Blesk.type.label, modifier = Modifier.weight(1f, fill = false).padding(vertical = 10.dp))
            IconButton(BleskIcons.Close, stringResource(R.string.design_hint_close), onClose)
        }
    }
}
