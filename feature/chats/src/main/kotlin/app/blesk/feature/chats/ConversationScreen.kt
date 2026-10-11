package app.blesk.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.SendStatus
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Capsule
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ConversationScreen(
    conversation: Conversation,
    messages: ImmutableList<Message>,
    authorName: (String) -> String?,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }
    val showAuthors = conversation.kind == ConversationKind.Group || conversation.kind == ConversationKind.Room
    Column(
        modifier
            .fillMaxSize()
            .background(Blesk.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(BleskIcons.Back, stringResource(app.blesk.core.design.R.string.design_back), onBack)
            Avatar(conversation.title, size = 36.dp)
            Text(conversation.title, style = Blesk.type.headline, maxLines = 1, modifier = Modifier.weight(1f))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Blesk.dimens.gutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "note") { ProtectionNote(conversation.protection) }
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    author = if (showAuthors && !message.isOwn) authorName(message.authorId) else null,
                )
            }
        }
        if (conversation.kind == ConversationKind.Channel) {
            Text(
                text = stringResource(R.string.chat_channel_readonly),
                style = Blesk.type.label,
                color = Blesk.colors.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(Blesk.dimens.gutter),
            )
        } else {
            Composer(onSend = onSend)
        }
    }
}

@Composable
private fun ProtectionNote(protection: Protection) {
    Text(
        text = stringResource(if (protection == Protection.EndToEnd) R.string.chat_encrypted_note else R.string.chat_open_note),
        style = Blesk.type.caption,
        color = Blesk.colors.textTertiary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        textAlign = TextAlign.Center,
    )
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun MessageBubble(message: Message, author: String?) {
    val own = message.isOwn
    val status = if (own) stringResource(statusLabel(message.status)) else null
    val time = timeFormat.format(message.sentAt.atZone(ZoneId.systemDefault()))
    Box(Modifier.fillMaxWidth(), contentAlignment = if (own) Alignment.CenterEnd else Alignment.CenterStart) {
        Capsule(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(author, message.text, time, status).joinToString(", ")
                },
            shape = RoundedCornerShape(20.dp),
            color = if (own) Blesk.colors.surfaceStrong else Blesk.colors.surface,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                if (author != null) Text(author, style = Blesk.type.label, color = Blesk.colors.textSecondary)
                Text(message.text)
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(time, style = Blesk.type.caption, color = Blesk.colors.textTertiary)
                    if (status != null) Text(status, style = Blesk.type.caption, color = Blesk.colors.textTertiary)
                }
            }
        }
    }
}

private fun statusLabel(status: SendStatus) = when (status) {
    SendStatus.Sending -> R.string.chat_status_sending
    SendStatus.Sent -> R.string.chat_status_sent
    SendStatus.Delivered -> R.string.chat_status_delivered
    SendStatus.Read -> R.string.chat_status_read
}

@Composable
private fun Composer(onSend: (String) -> Unit) {
    val state = rememberTextFieldState()
    val hint = stringResource(R.string.chat_input_hint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = Blesk.dimens.gutter - 4.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
            .background(Blesk.colors.surface)
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            if (state.text.isEmpty()) Text(hint, color = Blesk.colors.textTertiary)
            BasicTextField(
                state = state,
                textStyle = Blesk.type.body.copy(color = Blesk.colors.textPrimary),
                cursorBrush = SolidColor(Blesk.colors.accent),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .semantics { contentDescription = hint },
            )
        }
        IconButton(
            icon = BleskIcons.Send,
            contentDescription = stringResource(R.string.chat_send),
            onClick = {
                val text = state.text.toString()
                if (text.isNotBlank()) {
                    onSend(text)
                    state.clearText()
                }
            },
        )
    }
}
