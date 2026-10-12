package app.blesk.feature.chats

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.blesk.core.data.model.Contact
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.StateMessage
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList

/** Everyone you blocked, each with a way back. */
@Composable
fun BlockedScreen(people: ImmutableList<Contact>, onUnblock: (Contact) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScrollScreen(title = stringResource(R.string.blocked_title), onBack = onBack, modifier = modifier) {
        if (people.isEmpty()) {
            StateMessage(stringResource(R.string.blocked_empty))
            return@ScrollScreen
        }
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
            people.forEach { person ->
                ListRow(
                    title = person.name,
                    subtitle = "@${person.username}",
                    showChevron = false,
                    trailing = {
                        Button(stringResource(R.string.person_unblock), onClick = { onUnblock(person) }, kind = ButtonKind.Secondary)
                    },
                )
            }
        }
    }
}
