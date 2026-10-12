package app.blesk.feature.chats

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Contact
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList

/** Ways to start talking; null hides an action that makes no sense here (the demo has no server). */
class NewChatActions(
    val onScan: () -> Unit,
    val onNewGroup: (() -> Unit)?,
    val onNewChannel: (() -> Unit)?,
    val onInvite: (() -> Unit)?,
    val onOpenContact: (Contact) -> Unit,
    val onFind: (suspend (String) -> Boolean)?,
    val spaces: SpaceSearch?,
)

@Composable
fun NewChatScreen(contacts: ImmutableList<Contact>, actions: NewChatActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    LensScaffold(
        modifier = modifier,
        top = { TopLens(title = stringResource(R.string.new_chat_title), onBack = onBack) },
    ) { padding ->
        ContactList(
            contacts = contacts,
            contentPadding = padding,
            onOpenContact = actions.onOpenContact,
            onFind = actions.onFind,
            spaces = actions.spaces,
            header = {
                item(key = "actions") {
                    Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = 8.dp)) {
                        ListRow(title = stringResource(R.string.new_chat_scan), icon = BleskIcons.Qr, onClick = actions.onScan)
                        actions.onNewGroup?.let {
                            ListRow(title = stringResource(R.string.new_chat_group), icon = BleskIcons.People, onClick = it)
                        }
                        actions.onNewChannel?.let {
                            ListRow(title = stringResource(R.string.new_chat_channel), icon = BleskIcons.Channel, onClick = it)
                        }
                        actions.onInvite?.let {
                            ListRow(title = stringResource(R.string.new_chat_invite), icon = BleskIcons.Upload, onClick = it)
                        }
                    }
                }
            },
        )
    }
}
