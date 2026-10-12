package app.blesk.feature.chats.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.glass.lens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.Playback
import app.blesk.feature.chats.R

/**
 * What is playing, on a small capsule of the lens glass above the bottom lens: the name, a line
 * of the theme's light for how far along it is, play or pause, and a cross to stop.
 */
@Composable
fun MiniPlayer(playback: Playback, onToggle: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = playback.path != null,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(Blesk.dimens.capsuleRadius)
        Column(
            Modifier
                .padding(horizontal = Blesk.dimens.gutter)
                .padding(bottom = 8.dp)
                .fillMaxWidth()
                .lens(shape)
                .clip(shape),
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(playback.title.ifEmpty { stringResource(R.string.player_untitled) }, style = Blesk.type.bodyStrong, maxLines = 1)
                    if (playback.artist.isNotEmpty()) {
                        Text(playback.artist, style = Blesk.type.caption, color = Blesk.colors.textSecondary, maxLines = 1)
                    }
                }
                IconButton(
                    icon = if (playback.playing) BleskIcons.Pause else BleskIcons.Play,
                    contentDescription = stringResource(if (playback.playing) R.string.media_pause else R.string.media_play),
                    onClick = onToggle,
                )
                IconButton(BleskIcons.Close, stringResource(R.string.player_stop), onStop)
            }
            val fraction = if (playback.durationMillis > 0) {
                (playback.positionMillis.toFloat() / playback.durationMillis).coerceIn(0f, 1f)
            } else {
                0f
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Blesk.colors.hairline)) {
                Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(Blesk.colors.light, CircleShape))
            }
        }
    }
}
