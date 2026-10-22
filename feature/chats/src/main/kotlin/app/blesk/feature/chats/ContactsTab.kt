package app.blesk.feature.chats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Contact
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.MenuSheet
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.StateMessage
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

private enum class ContactOrder { Name, Recent, Online }

class ContactActions(
    val onOpen: (Contact) -> Unit,
    val onWrite: (Contact) -> Unit,
    /** Calls the person; null where calls are not offered. */
    val onCall: ((Contact) -> Unit)? = null,
    /** Shares an invitation; null in demo mode, where there is nobody to invite. */
    val onInvite: (() -> Unit)?,
    /** Makes them a stranger again; the chat stays. */
    val onRemove: ((Contact) -> Unit)? = null,
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
    /** When you last wrote to each person, by contact id; it orders «Недавние». */
    lastWritten: Map<String, Instant> = emptyMap(),
    /** Who is online right now, by contact id. */
    online: Set<String> = emptySet(),
) {
    var order by rememberSaveable { mutableStateOf(ContactOrder.Name) }
    var menuFor by remember { mutableStateOf<Contact?>(null) }
    val shown = remember(contacts, query, order, lastWritten, online) {
        val needle = query.trim().removePrefix("@")
        val byName = compareBy(String.CASE_INSENSITIVE_ORDER) { contact: Contact -> contact.name }
        contacts
            .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) || it.username.contains(needle, ignoreCase = true) }
            .sortedWith(
                when (order) {
                    ContactOrder.Name -> byName
                    ContactOrder.Recent -> compareByDescending<Contact> { lastWritten[it.id] }.then(byName)
                    ContactOrder.Online -> compareByDescending<Contact> { it.id in online }.then(byName)
                },
            )
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
        if (query.isBlank() && contacts.size > 1) {
            item(key = "sort") {
                Segmented(
                    options = persistentListOf(
                        stringResource(R.string.contacts_by_name),
                        stringResource(R.string.contacts_recent),
                        stringResource(R.string.contacts_online),
                    ),
                    selected = order.ordinal,
                    onSelect = { order = ContactOrder.entries[it] },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                StateMessage(stringResource(if (query.isBlank()) R.string.contacts_empty else R.string.contacts_nothing_found))
            }
        }
        items(shown, key = { it.id }) { contact -> ContactRow(contact, contact.id in online, actions) { menuFor = contact } }
    }
    menuFor?.let { contact -> ContactMenu(contact, actions) { menuFor = null } }
}

@Composable
private fun ContactMenu(contact: Contact, actions: ContactActions, onDismiss: () -> Unit) {
    fun act(action: (Contact) -> Unit) {
        onDismiss()
        action(contact)
    }
    MenuSheet(title = contact.name, onDismiss = onDismiss) {
        Card {
            ListRow(
                title = stringResource(R.string.contacts_write),
                icon = BleskIcons.Chats,
                showChevron = false,
                onClick = { act(actions.onWrite) },
            )
            actions.onCall?.let { call ->
                ListRow(stringResource(R.string.person_call), icon = BleskIcons.Phone, showChevron = false, onClick = { act(call) })
            }
            ListRow(
                title = stringResource(R.string.contacts_profile),
                icon = BleskIcons.Person,
                showChevron = false,
                onClick = { act(actions.onOpen) },
            )
            actions.onRemove?.let { remove ->
                ListRow(
                    title = stringResource(R.string.contacts_remove),
                    subtitle = stringResource(R.string.contacts_remove_hint),
                    icon = BleskIcons.Trash,
                    showChevron = false,
                    onClick = { act(remove) },
                )
            }
        }
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
private fun ContactRow(contact: Contact, online: Boolean, actions: ContactActions, onMenu: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, label = "press")
    val capsule = Blesk.colors.surface
    val writeLabel = stringResource(R.string.contacts_write)
    val callLabel = stringResource(R.string.person_call)
    val haptics = rememberHaptics()
    SideSwipe(
        left = actions.onCall?.let { call -> SwipeAction(BleskIcons.Phone, callLabel) { call(contact) } },
        right = SwipeAction(BleskIcons.Chats, writeLabel) { actions.onWrite(contact) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
                .drawBehind { if (press > 0f) drawRect(capsule.copy(alpha = capsule.alpha * press)) }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.contacts_open_profile),
                    onLongClickLabel = stringResource(R.string.contacts_menu),
                    onLongClick = {
                        haptics.menu()
                        onMenu()
                    },
                ) { actions.onOpen(contact) }
                .semantics(mergeDescendants = true) {}
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Avatar(contact.name, key = contact.username, online = online, photoFile = contact.photo)
            Column(Modifier.weight(1f)) {
                Text(contact.name, style = Blesk.type.bodyStrong, maxLines = 1)
                Text("@${contact.username}", style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
            }
            IconButton(BleskIcons.Chats, writeLabel, { actions.onWrite(contact) })
        }
    }
}
