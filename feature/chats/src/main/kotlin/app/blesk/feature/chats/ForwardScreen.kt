package app.blesk.feature.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Conversation
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Switch
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList

/** Picks the chats a message goes to. */
@Composable
fun ForwardScreen(
    conversations: ImmutableList<Conversation>,
    onSend: (List<String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picked = remember { mutableStateListOf<String>() }
    ScrollScreen(
        title = stringResource(R.string.forward_title),
        onBack = onBack,
        modifier = modifier,
        // Appears with the first chat picked: with none it would have nothing to do.
        bottom = {
            if (picked.isNotEmpty()) {
                Button(
                    text = stringResource(R.string.forward_send, picked.size),
                    onClick = { onSend(picked.toList()) },
                    modifier = Modifier.padding(Blesk.dimens.gutter).fillMaxWidth(),
                )
            }
        },
    ) {
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
            conversations.forEach { conversation ->
                val checked = conversation.id in picked
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = checked, role = Role.Checkbox) {
                            if (it) picked += conversation.id else picked -= conversation.id
                        }
                        .padding(horizontal = Blesk.dimens.gutter, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Avatar(conversation.title, size = 40.dp)
                    Text(conversation.title, style = Blesk.type.bodyStrong, maxLines = 1, modifier = Modifier.weight(1f))
                    Switch(checked)
                }
            }
        }
    }
}
