package app.blesk.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList

/** One signature animation: what it is, and a stage that plays it again for every new replay. */
class MotionDemo(val title: String, val note: String, val stage: @Composable (replay: Int) -> Unit)

/** One vibration of the dictionary: what it means, and how to feel it. */
class HapticDemo(val title: String, val note: String, val play: () -> Unit)

/**
 * «Витрина движения»: every signature animation and vibration from the brief, each played on its
 * own, so the owner can check them one by one on a real phone.
 */
@Composable
fun MotionShowcase(
    animations: ImmutableList<MotionDemo>,
    haptics: ImmutableList<HapticDemo>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    ScrollScreen(title = stringResource(R.string.motion_title), onBack = onBack, modifier = modifier) {
        Text(stringResource(R.string.motion_intro), color = Blesk.colors.textSecondary, modifier = pad.padding(bottom = 8.dp))
        SectionHeader(stringResource(R.string.motion_animations))
        Card(pad) {
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                animations.forEach { demo -> Demo(demo) }
            }
        }
        SectionHeader(stringResource(R.string.motion_haptics))
        Card(pad) {
            haptics.forEach { haptic ->
                ListRow(title = haptic.title, subtitle = haptic.note, showChevron = false, onClick = haptic.play)
            }
        }
    }
}

@Composable
private fun Demo(demo: MotionDemo) {
    var replay by remember { mutableIntStateOf(0) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(demo.title, style = Blesk.type.bodyStrong)
                Text(demo.note, style = Blesk.type.label, color = Blesk.colors.textSecondary)
            }
            Button(text = stringResource(R.string.motion_play), kind = ButtonKind.Secondary, onClick = { replay++ })
        }
        Box(Modifier.fillMaxWidth()) { demo.stage(replay) }
    }
}
