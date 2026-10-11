package app.blesk.feature.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Contact
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Capsule
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.NotEncryptedTag
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.shared.protocol.SpaceInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
fun ContactList(
    contacts: ImmutableList<Contact>,
    contentPadding: PaddingValues,
    onOpenContact: (Contact) -> Unit,
    modifier: Modifier = Modifier,
    /** Finds someone on the server by username and opens the chat; null in demo mode. */
    onFind: (suspend (String) -> Boolean)? = null,
    /** Public channels and open rooms matching the same query; null where there are none. */
    spaces: SpaceSearch? = null,
) {
    var found by remember { mutableStateOf<ImmutableList<SpaceInfo>>(persistentListOf()) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Blesk.dimens.rowGap),
    ) {
        if (onFind != null) {
            item(key = "find") {
                FindPerson { query ->
                    val person = onFind(query)
                    found = spaces?.search?.invoke(query).orEmpty().toImmutableList()
                    person || found.isNotEmpty()
                }
            }
        }
        if (found.isNotEmpty() && spaces != null) {
            item(key = "spaces") { SectionHeader(stringResource(R.string.space_found)) }
            items(found, key = { "space:" + it.handle }) { space -> FoundSpace(space, spaces.join) }
        }
        items(contacts, key = { it.id }) { contact ->
            Capsule(
                modifier = Modifier
                    .padding(horizontal = Blesk.dimens.gutter - 6.dp)
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { },
                onClick = { onOpenContact(contact) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Avatar(contact.name)
                    Column {
                        Text(contact.name, style = Blesk.type.bodyStrong, maxLines = 1)
                        Text("@${contact.username}", style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Search and join for public spaces. */
class SpaceSearch(val search: suspend (String) -> List<SpaceInfo>, val join: suspend (String) -> Unit)

@Composable
private fun FoundSpace(space: SpaceInfo, join: suspend (String) -> Unit) {
    val scope = rememberCoroutineScope()
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        Row(
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Avatar(space.title)
            Column(Modifier.weight(1f)) {
                Text(space.title, style = Blesk.type.bodyStrong, maxLines = 1)
                Text(
                    text = "@${space.handle} · ${pluralStringResource(R.plurals.space_members, space.members, space.members)}",
                    style = Blesk.type.label,
                    color = Blesk.colors.textSecondary,
                    maxLines = 1,
                )
            }
            Button(
                text = stringResource(if (space.role != null) R.string.chat_open else R.string.space_join),
                kind = ButtonKind.Secondary,
                onClick = { scope.launch { join(space.handle) } },
            )
        }
        if (space.about.isNotBlank()) {
            Text(
                text = space.about,
                style = Blesk.type.label,
                color = Blesk.colors.textSecondary,
                maxLines = 3,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = 12.dp),
            )
        }
        NotEncryptedTag(Modifier.padding(start = Blesk.dimens.gutter, bottom = 12.dp))
    }
}

@Composable
private fun FindPerson(onFind: suspend (String) -> Boolean) {
    val state = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    var notFound by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 8.dp)) {
        TextInput(
            label = stringResource(R.string.contacts_find),
            state = state,
            hint = if (notFound) stringResource(R.string.contacts_not_found) else stringResource(R.string.contacts_find_hint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
        Button(
            text = stringResource(R.string.contacts_find_action),
            icon = BleskIcons.Search,
            onClick = {
                if (busy) return@Button
                busy = true
                scope.launch {
                    notFound = !onFind(state.text.toString().trim())
                    busy = false
                }
            },
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter),
        )
    }
}
