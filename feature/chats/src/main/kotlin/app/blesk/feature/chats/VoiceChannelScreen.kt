package app.blesk.feature.chats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.motion.rememberLoopPhase
import app.blesk.core.design.theme.Blesk
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A voice channel: the people in it gather into one cluster; whoever talks glows. */
@Composable
fun VoiceChannelScreen(channel: RoomChannel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScrollScreen(title = channel.title, onBack = onBack, modifier = modifier) {
        val count = channel.participants.size
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            ParticipantCluster(channel)
        }
        Text(
            text = pluralStringResource(R.plurals.voice_count, count, count),
            style = Blesk.type.bodyStrong,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.voice_hint),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 16.dp)) {
            ListRow(title = stringResource(R.string.voice_join), icon = BleskIcons.Voice, soon = true)
        }
    }
}

@Composable
private fun ParticipantCluster(channel: RoomChannel) {
    val pulse by rememberLoopPhase(1600)
    val reduced = Blesk.motion.reduced
    val light = Blesk.colors.light
    val people = channel.participants
    val avatar = 64.dp
    val names = people.joinToString { it.name }
    Box(
        modifier = Modifier
            .size(220.dp)
            .semantics { contentDescription = names }
            .metaballs(Blesk.colors.surface, smoothness = 36.dp) {
                val r = avatar.toPx() / 2f + 8.dp.toPx()
                positions(people.size, size.width / 2f, size.height / 2f, avatar.toPx() * 0.62f).map { (x, y) ->
                    Blob.circle(x, y, r)
                }
            }
            .drawBehind {
                val r = avatar.toPx() / 2f + 4.dp.toPx()
                positions(people.size, size.width / 2f, size.height / 2f, avatar.toPx() * 0.62f).forEachIndexed { i, (x, y) ->
                    if (people[i].id in channel.speaking) {
                        val glow = if (reduced) 0.8f else 0.5f + 0.5f * sin(pulse * 2f * PI).toFloat()
                        drawCircle(light.copy(alpha = glow), r, Offset(x, y), style = Stroke(2.dp.toPx()))
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        val sizePx = with(density) { 220.dp.toPx() }
        positions(people.size, sizePx / 2f, sizePx / 2f, with(density) { avatar.toPx() } * 0.62f).forEachIndexed { i, (x, y) ->
            Avatar(
                name = people[i].name,
                size = avatar,
                modifier = Modifier.padding(
                    start = with(density) { (x - avatar.toPx() / 2f).toDp() },
                    top = with(density) { (y - avatar.toPx() / 2f).toDp() },
                ),
            )
        }
    }
}

private fun positions(count: Int, cx: Float, cy: Float, spread: Float): List<Pair<Float, Float>> = when (count) {
    0 -> emptyList()
    1 -> listOf(cx to cy)
    else -> (0 until count).map { i ->
        val angle = 2.0 * PI * i / count - PI / 2
        (cx + spread * cos(angle).toFloat()) to (cy + spread * sin(angle).toFloat())
    }
}
