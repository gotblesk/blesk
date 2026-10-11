package app.blesk.feature.chats

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Message
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.launch

/** Quick reactions offered in the message menu. User content, not interface icons. */
internal val QuickReactions = listOf("❤️", "👍", "🔥", "😂", "😮", "😢")

/** A matte sheet with reactions and actions for one message. */
@Composable
internal fun MessageMenu(
    message: Message,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: ((String) -> Unit)?,
    editable: Boolean = true,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val closeLabel = stringResource(R.string.chat_close)
    fun act(action: () -> Unit) {
        action()
        onDismiss()
    }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = closeLabel,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(Blesk.dimens.gutter)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onReact != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
                        .background(Blesk.colors.surface)
                        .padding(6.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    QuickReactions.forEach { symbol ->
                        val label = stringResource(R.string.chat_react, symbol)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { act { onReact(symbol) } }
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(symbol, style = Blesk.type.headline)
                        }
                    }
                }
            }
            Card {
                ListRow(
                    title = stringResource(R.string.chat_reply),
                    icon = BleskIcons.Back,
                    showChevron = false,
                    onClick = { act(onReply) },
                )
                ListRow(
                    title = stringResource(R.string.chat_copy),
                    icon = BleskIcons.Copy,
                    showChevron = false,
                    onClick = {
                        act {
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("blesk", message.text))) }
                        }
                    },
                )
                if (message.isOwn && editable) {
                    ListRow(
                        title = stringResource(R.string.chat_edit),
                        icon = BleskIcons.Edit,
                        showChevron = false,
                        onClick = { act(onEdit) },
                    )
                    ListRow(
                        title = stringResource(R.string.chat_delete),
                        icon = BleskIcons.Trash,
                        showChevron = false,
                        onClick = { act(onDelete) },
                    )
                }
            }
        }
    }
}
