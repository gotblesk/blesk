package app.blesk.feature.chats.media

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Choice
import app.blesk.core.design.component.ChoiceSheet
import app.blesk.core.design.component.HeaderArt
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.MenuSheet
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.component.avatarHue
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A small square cover: your own picture or the one from the tags, else «Свет» in the title's hue. */
@Composable
internal fun CoverThumb(title: String, path: String?, modifier: Modifier = Modifier) {
    val picture by produceState<ImageBitmap?>(null, path) {
        value = path?.let { file ->
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file)?.asImageBitmap() }.getOrNull() }
        }
    }
    Box(modifier.size(ThumbSide).clip(RoundedCornerShape(ThumbCorner))) {
        val shown = picture
        if (shown != null) {
            Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val preset = title.hashCode().mod(HeaderStyle.Light.PRESETS)
            HeaderArt(HeaderStyle.Light(preset), photo = null, modifier = Modifier.fillMaxSize(), hue = avatarHue(title))
        }
    }
}

@Composable
internal fun OwnSongRow(song: OwnSong, playing: Boolean, onPlay: () -> Unit, onMenu: (() -> Unit)?) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClick = onPlay,
                onLongClickLabel = onMenu?.let { stringResource(R.string.music_actions) },
                onLongClick = onMenu?.let {
                    {
                        haptics.menu()
                        it()
                    }
                },
            )
            .padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverThumb(song.title, song.cover)
        Column(Modifier.weight(1f)) {
            Text(song.title, style = Blesk.type.bodyStrong, maxLines = 1)
            if (song.artist.isNotEmpty()) Text(song.artist, style = Blesk.type.caption, color = Blesk.colors.textSecondary, maxLines = 1)
        }
        if (playing) Icon(icon = BleskIcons.Pause, contentDescription = null, tint = Blesk.colors.textPrimary, size = 18.dp)
        Text(clock(song.durationMillis), style = Blesk.type.caption, color = Blesk.colors.textSecondary)
    }
}

private enum class OwnSheet { Menu, Rename, Delete }

/** A long press on your own song: rename, cover, send to a chat, delete. */
@Composable
internal fun OwnSongSheet(song: OwnSong, actions: OwnMusicActions, onClose: () -> Unit) {
    var sheet by remember { mutableStateOf(OwnSheet.Menu) }
    when (sheet) {
        OwnSheet.Menu -> MenuSheet(title = song.title, onDismiss = onClose) {
            Card {
                ListRow(title = stringResource(R.string.music_rename), icon = BleskIcons.Edit, onClick = { sheet = OwnSheet.Rename })
                ListRow(
                    title = stringResource(R.string.music_cover),
                    icon = BleskIcons.Image,
                    onClick = {
                        onClose()
                        actions.pickCover(song.id)
                    },
                )
                if (song.ownCover) {
                    ListRow(
                        title = stringResource(R.string.music_cover_remove),
                        icon = BleskIcons.Close,
                        showChevron = false,
                        onClick = {
                            onClose()
                            actions.removeCover(song.id)
                        },
                    )
                }
                ListRow(
                    title = stringResource(R.string.music_send),
                    icon = BleskIcons.Forward,
                    onClick = {
                        onClose()
                        actions.send(song.id)
                    },
                )
                ListRow(
                    title = stringResource(R.string.music_delete),
                    icon = BleskIcons.Trash,
                    showChevron = false,
                    onClick = { sheet = OwnSheet.Delete },
                )
            }
        }
        OwnSheet.Rename -> MenuSheet(title = stringResource(R.string.music_rename), onDismiss = onClose) {
            val title = rememberTextFieldState(song.title)
            val artist = rememberTextFieldState(song.artist)
            TextInput(label = stringResource(R.string.music_title_field), state = title)
            TextInput(label = stringResource(R.string.music_artist_field), state = artist)
            Button(
                text = stringResource(R.string.music_save),
                onClick = {
                    actions.rename(song.id, title.text.toString(), artist.text.toString())
                    onClose()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        OwnSheet.Delete -> ChoiceSheet(
            title = song.title,
            text = stringResource(R.string.music_delete_hint),
            choices = listOf(
                Choice(stringResource(R.string.music_delete), danger = true) {
                    actions.remove(song.id)
                    onClose()
                },
            ),
            onDismiss = onClose,
        )
    }
}

private val ThumbSide = 44.dp
private val ThumbCorner = 10.dp
