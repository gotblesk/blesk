package app.blesk.feature.chats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Contact
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.StateMessage
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList

class ContactActions(
    val onOpen: (Contact) -> Unit,
    val onWrite: (Contact) -> Unit,
    /** Shares an invitation; null in demo mode, where there is nobody to invite. */
    val onInvite: (() -> Unit)?,
)

/**
 * The «Контакты» tab: people you have written to or added, by name, as rows on the background.
 * [query] narrows the list by name or username while the search lens is open.
 */
@Composable
fun ContactsTab(
    contacts: ImmutableList<Contact>,
    query: String,
    contentPadding: PaddingValues,
    actions: ContactActions,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    val shown = remember(contacts, query) {
        val needle = query.trim().removePrefix("@")
        contacts
            .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) || it.username.contains(needle, ignoreCase = true) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(
            start = Blesk.dimens.gutter - 6.dp,
            end = Blesk.dimens.gutter - 6.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        if (actions.onInvite != null && query.isBlank()) {
            item(key = "invite") { InviteRow(actions.onInvite) }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                StateMessage(stringResource(if (query.isBlank()) R.string.contacts_empty else R.string.contacts_nothing_found))
            }
        }
        items(shown, key = { it.id }) { contact -> ContactRow(contact, actions) }
    }
}

@Composable
private fun InviteRow(onInvite: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
            .clickable(role = Role.Button, onClick = onInvite)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(Blesk.dimens.avatar).background(Blesk.colors.surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(BleskIcons.Invite, contentDescription = null, tint = Blesk.colors.textPrimary)
        }
        Text(stringResource(R.string.contacts_invite), style = Blesk.type.bodyStrong, modifier = Modifier.weight(1f))
    }
}

/** A person on the background; a capsule rises under the finger while it is pressed. */
@Composable
private fun ContactRow(contact: Contact, actions: ContactActions) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, label = "press")
    val capsule = Blesk.colors.surface
    val writeLabel = stringResource(R.string.contacts_write)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
            .drawBehind { if (press > 0f) drawRect(capsule.copy(alpha = capsule.alpha * press)) }
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = stringResource(R.string.contacts_open_profile),
            ) { actions.onOpen(contact) }
            .semantics(mergeDescendants = true) {
                customActions = listOf(CustomAccessibilityAction(writeLabel) { actions.onWrite(contact); true })
            }
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(contact.name, key = contact.username)
        Column(Modifier.weight(1f)) {
            Text(contact.name, style = Blesk.type.bodyStrong, maxLines = 1)
            Text("@${contact.username}", style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
        }
        IconButton(BleskIcons.Chats, writeLabel, { actions.onWrite(contact) })
    }
}
