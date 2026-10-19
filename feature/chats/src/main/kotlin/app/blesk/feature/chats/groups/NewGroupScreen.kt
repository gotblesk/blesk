package app.blesk.feature.chats.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.blesk.core.data.SpaceResult
import app.blesk.core.data.model.Contact
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Switch
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

/** The four things you can start: closed (encrypted) group or room, public channel or open room. */
enum class NewKind { Group, Room, Channel, OpenRoom }

/** What the screen asks for; [handle] and [about] matter only for public kinds. */
data class NewSpec(val kind: NewKind, val title: String, val members: List<String>, val handle: String, val about: String)

@Composable
fun NewGroupScreen(
    contacts: ImmutableList<Contact>,
    onCreate: suspend (NewSpec) -> SpaceResult,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialKind: NewKind = NewKind.Group,
) {
    val scope = rememberCoroutineScope()
    val title = rememberTextFieldState()
    val handle = rememberTextFieldState()
    val about = rememberTextFieldState()
    var kind by remember { mutableStateOf(initialKind) }
    val picked = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SpaceResult?>(null) }
    var nameMissing by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val public = kind == NewKind.Channel || kind == NewKind.OpenRoom
    ScrollScreen(title = stringResource(R.string.group_new_title), onBack = onBack, modifier = modifier) {
        Segmented(
            options = persistentListOf(
                stringResource(R.string.group_kind_group),
                stringResource(R.string.group_kind_room),
                stringResource(R.string.group_kind_channel),
                stringResource(R.string.group_kind_open),
            ),
            selected = kind.ordinal,
            onSelect = { kind = NewKind.entries[it] },
        )
        Text(
            text = stringResource(
                when (kind) {
                    NewKind.Group -> R.string.group_kind_hint_group
                    NewKind.Room -> R.string.group_kind_hint_room
                    NewKind.Channel -> R.string.group_kind_hint_channel
                    NewKind.OpenRoom -> R.string.group_kind_hint_open
                },
            ),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 4.dp),
        )
        TextInput(label = stringResource(R.string.group_name), state = title)
        if (public) {
            TextInput(
                label = stringResource(R.string.space_handle),
                state = handle,
                hint = when (result) {
                    SpaceResult.HandleTaken -> stringResource(R.string.space_error_taken)
                    SpaceResult.HandleInvalid -> stringResource(R.string.space_error_invalid)
                    else -> stringResource(R.string.space_handle_hint)
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            )
            TextInput(label = stringResource(R.string.space_about), state = about, singleLine = false)
        } else {
            SectionHeader(stringResource(R.string.group_pick_contacts))
            ContactPicker(contacts, picked)
        }
        if (nameMissing && title.text.isBlank()) {
            Text(
                text = stringResource(R.string.group_name_required),
                color = Blesk.colors.danger,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (result == SpaceResult.Failed) {
            Text(
                text = stringResource(R.string.space_error_failed),
                color = Blesk.colors.danger,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(
            text = stringResource(R.string.group_create),
            onClick = {
                val name = title.text.toString().trim()
                if (busy) return@Button
                if (name.isEmpty()) {
                    nameMissing = true
                    haptics.refuse()
                    return@Button
                }
                busy = true
                scope.launch {
                    val spec = NewSpec(
                        kind = kind,
                        title = name,
                        members = picked.toList(),
                        handle = handle.text.toString().trim().removePrefix("@").lowercase(),
                        about = about.text.toString(),
                    )
                    result = onCreate(spec)
                    busy = false
                }
            },
            modifier = Modifier.padding(Blesk.dimens.gutter).fillMaxWidth(),
        )
    }
}

/** Checkable contacts; the list of picked usernames is updated in place. */
@Composable
internal fun ContactPicker(contacts: ImmutableList<Contact>, picked: MutableList<String>) {
    if (contacts.isEmpty()) {
        Text(
            text = stringResource(R.string.group_no_contacts),
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp),
        )
        return
    }
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        contacts.forEach { contact ->
            val checked = contact.username in picked
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, role = Role.Checkbox) {
                        if (it) picked += contact.username else picked -= contact.username
                    }
                    .padding(horizontal = Blesk.dimens.gutter, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Avatar(contact.name, size = 40.dp)
                Text(contact.name, style = Blesk.type.bodyStrong, modifier = Modifier.weight(1f))
                Switch(checked)
            }
        }
    }
}
