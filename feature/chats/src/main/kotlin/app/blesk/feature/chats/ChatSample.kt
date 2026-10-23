package app.blesk.feature.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.ME
import app.blesk.core.data.model.Message
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.LocalClock
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

/**
 * A few lines of a protected chat drawn with the real chat parts, as a preview of a theme: the
 * header with Аня and her lock, two of her messages and one of yours. It is a picture, not a chat:
 * it takes no taps, and TalkBack hears what it is.
 */
@Composable
fun ChatSample(modifier: Modifier = Modifier) {
    val clock = LocalClock.current
    val now = remember(clock) { clock() }
    val name = stringResource(R.string.chats_sample_name)
    val texts = listOf(
        stringResource(R.string.chats_sample_in_first),
        stringResource(R.string.chats_sample_out),
        stringResource(R.string.chats_sample_in_second),
    )
    val description = stringResource(R.string.chats_sample_description)
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(name, size = HeaderAvatar, protected = true, key = SAMPLE_PERSON)
            Column(Modifier.weight(1f)) {
                Text(name, style = Blesk.type.bodyStrong, maxLines = 1)
                Text(stringResource(R.string.chat_online), style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
            }
            Icon(BleskIcons.Lock, contentDescription = null, tint = Blesk.colors.textSecondary, size = 18.dp)
        }
        texts.forEachIndexed { index, text ->
            val own = index == OWN
            MessageColumn(
                messages = persistentListOf(sampleMessage(index, own, text, now)),
                author = null,
                fresh = emptySet(),
                quoteText = { null },
                actions = QuietActions,
            )
        }
    }
}

private fun sampleMessage(index: Int, own: Boolean, text: String, at: Instant) = Message(
    id = "sample$index",
    conversationId = SAMPLE_PERSON,
    authorId = if (own) ME else SAMPLE_PERSON,
    text = text,
    sentAt = at,
)

private const val SAMPLE_PERSON = "anya"
private const val OWN = 1
private val HeaderAvatar = 36.dp
