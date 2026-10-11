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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Contact
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Capsule
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ContactList(
    contacts: ImmutableList<Contact>,
    contentPadding: PaddingValues,
    onOpenContact: (Contact) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Blesk.dimens.rowGap),
    ) {
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
