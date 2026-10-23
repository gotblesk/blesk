package app.blesk.feature.chats.groups

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.MenuSheet
import app.blesk.core.design.component.QrCode
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupInvite
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.Right
import app.blesk.shared.protocol.RoomRole
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
internal fun InvitesPage(env: ManageEnv, now: Long, onSheet: (ManageSheet) -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru")) }
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        if (env.state.invites.isEmpty()) Empty(R.string.manage_invites_empty)
        env.state.invites.asReversed().forEach { invite ->
            val parts = buildList {
                add(
                    invite.expiresAt?.let {
                        val until = formatter.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
                        stringResource(R.string.manage_invite_until, until)
                    } ?: stringResource(R.string.manage_invite_no_expiry),
                )
                add(
                    invite.limit?.let { stringResource(R.string.manage_invite_used, invite.joined.size, it) }
                        ?: pluralStringResource(R.plurals.manage_invite_joined_count, invite.joined.size, invite.joined.size),
                )
                when {
                    invite.revoked -> add(stringResource(R.string.manage_invite_revoked))
                    !invite.usable(now) -> add(stringResource(R.string.manage_invite_expired))
                }
            }
            ListRow(
                title = invite.title.ifBlank { stringResource(R.string.manage_invite_untitled) },
                subtitle = parts.joinToString(" · "),
                icon = BleskIcons.Invite,
                onClick = { onSheet(ManageSheet.Invite(invite.code)) },
            )
        }
    }
    NewInviteForm(env, now)
}

@Composable
private fun NewInviteForm(env: ManageEnv, now: Long) {
    val title = rememberTextFieldState()
    var expiry by remember { mutableIntStateOf(1) }
    var limit by remember { mutableIntStateOf(LIMITS.lastIndex) }
    SectionHeader(stringResource(R.string.manage_invite_new))
    TextInput(label = stringResource(R.string.manage_invite_name), state = title)
    Text(
        stringResource(R.string.manage_invite_expiry),
        style = Blesk.type.label,
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 6.dp),
    )
    Segmented(
        options = persistentListOf(
            stringResource(R.string.manage_invite_hour),
            stringResource(R.string.manage_invite_day),
            stringResource(R.string.manage_invite_week),
            stringResource(R.string.manage_invite_forever),
        ),
        selected = expiry,
        onSelect = { expiry = it },
    )
    Text(
        stringResource(R.string.manage_invite_limit),
        style = Blesk.type.label,
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 6.dp),
    )
    val unlimited = stringResource(R.string.manage_invite_unlimited)
    Segmented(
        options = LIMITS.map { it?.toString() ?: unlimited }.toImmutableList(),
        selected = limit,
        onSelect = { limit = it },
    )
    Button(
        text = stringResource(R.string.manage_invite_create),
        icon = BleskIcons.Plus,
        onClick = {
            val invite = GroupInvite.fresh(title.text.toString(), EXPIRIES[expiry]?.let { now + it }, LIMITS[limit])
            env.submit(GroupChange.NewInvite(invite))
            title.edit { replace(0, length, "") }
        },
        modifier = Modifier.fillMaxWidth().padding(Blesk.dimens.gutter),
    )
}

@Composable
internal fun InviteSheet(env: ManageEnv, invite: GroupInvite, now: Long, onClose: () -> Unit) {
    val link = env.inviteLink?.invoke(invite.code)
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notify = rememberNotify()
    val copied = stringResource(R.string.manage_invite_copied)
    var qr by remember { mutableStateOf(false) }
    MenuSheet(title = invite.title.ifBlank { stringResource(R.string.manage_invite_untitled) }, onDismiss = onClose) {
        Column(Modifier.heightIn(max = SheetMax).verticalScroll(rememberScrollState())) {
            if (link == null) {
                Text(
                    stringResource(R.string.manage_invite_needs_server),
                    style = Blesk.type.caption,
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(8.dp),
                )
            } else if (qr) {
                // Dark modules on white whatever the theme: many scanners cannot read inverted codes.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = QrMargin)
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .padding(20.dp),
                ) {
                    QrCode(link, Color.Black, Modifier.fillMaxWidth().aspectRatio(1f))
                }
            } else {
                Text(link, style = Blesk.type.caption, color = Blesk.colors.textSecondary, modifier = Modifier.padding(8.dp))
            }
            val usable = invite.usable(now)
            if (usable && link != null) {
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(stringResource(R.string.manage_invite_copy), icon = BleskIcons.Copy, kind = ButtonKind.Secondary, onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("blesk", link)))
                            notify(copied)
                        }
                    })
                    Button(
                        stringResource(R.string.manage_invite_qr),
                        icon = BleskIcons.Qr,
                        kind = ButtonKind.Secondary,
                        onClick = { qr = !qr },
                    )
                    Button(stringResource(R.string.manage_invite_share), icon = BleskIcons.Share, kind = ButtonKind.Secondary, onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                        context.startActivity(Intent.createChooser(send, null))
                    })
                }
            }
            Card {
                val joined = invite.joined.map(env.nameOf)
                ListRow(
                    title = if (joined.isEmpty()) {
                        stringResource(R.string.manage_invite_nobody)
                    } else {
                        stringResource(R.string.manage_invite_joined, joined.joinToString(", "))
                    },
                    icon = BleskIcons.People,
                    showChevron = false,
                )
                if (!invite.revoked) {
                    ListRow(
                        title = stringResource(R.string.manage_invite_revoke),
                        icon = BleskIcons.Close,
                        showChevron = false,
                        onClick = {
                            env.submit(GroupChange.Revoke(invite.code))
                            onClose()
                        },
                    )
                }
            }
        }
    }
}

/**
 * A room's roles in order of seniority, dragged to reorder; a tap opens one to change in [ManageSheet.Role]. Your own
 * role and those above it stay put and closed: only those above you change them.
 */
@Composable
internal fun RolesPage(env: ManageEnv, onSheet: (ManageSheet) -> Unit) {
    val untitled = stringResource(R.string.manage_role_untitled)
    val fresh = remember(env.state.roles) {
        RoomRole(UUID.randomUUID().toString().take(ROLE_ID), "", hue = env.state.roles.size % COMMUNITY_HUES)
    }
    val fixed = env.state.roles.count { !GroupPolicy.outranks(env.state, env.me, it.id) }
    Text(
        stringResource(R.string.manage_roles_hint),
        style = Blesk.type.caption,
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 8.dp),
    )
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        if (env.state.roles.isEmpty()) Empty(R.string.manage_roles_empty)
        ReorderColumn(
            items = env.state.roles,
            key = { it.id },
            fixed = fixed,
            onMove = { id, to, undo -> env.submit(GroupChange.MoveRole(id, to), undo) },
        ) { role ->
            val count = env.state.members.count { role.id in it.roles }
            ListRow(
                title = role.title.ifBlank { untitled },
                subtitle = pluralStringResource(R.plurals.manage_role_members, count, count),
                onClick = if (GroupPolicy.outranks(env.state, env.me, role.id)) ({ onSheet(ManageSheet.Role(role)) }) else null,
                trailing = { HueDot(role.hue, size = 12.dp) },
            )
        }
        ListRow(
            title = stringResource(R.string.manage_role_everyone),
            subtitle = pluralStringResource(
                R.plurals.manage_role_members,
                env.state.members.count { it.role == GroupRole.Member },
                env.state.members.count { it.role == GroupRole.Member },
            ),
            icon = BleskIcons.People,
            showChevron = false,
        )
    }
    if (fixed > 0) Hint(R.string.manage_roles_above_hint)
    if (env.allows(GroupChange.SaveRole(fresh))) {
        Button(
            text = stringResource(R.string.manage_role_new),
            icon = BleskIcons.Plus,
            kind = ButtonKind.Secondary,
            onClick = { onSheet(ManageSheet.Role(fresh)) },
            modifier = Modifier.padding(Blesk.dimens.gutter),
        )
    }
}

/** A role's name, hue and rights. A right you lack stays as the role has it: you may take it away, not give it. */
@Composable
internal fun RoleEditor(env: ManageEnv, role: RoomRole, onClose: () -> Unit) {
    val title = rememberTextFieldState(role.title)
    var hue by remember(role.id) { mutableIntStateOf(role.hue) }
    val rights = remember(role.id) { mutableStateListOf<Right>().apply { addAll(role.rights) } }
    val exists = env.state.roles.any { it.id == role.id }
    val untitled = stringResource(R.string.manage_role_untitled)
    fun offered(right: Right) = right in role.rights || env.can(right)
    MenuSheet(title = role.title.ifBlank { stringResource(R.string.manage_role_new) }, onDismiss = onClose) {
        Column(Modifier.heightIn(max = SheetMax).verticalScroll(rememberScrollState())) {
            TextInput(label = stringResource(R.string.manage_role_name), state = title)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.manage_role_hue),
                    style = Blesk.type.label,
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(start = Blesk.dimens.gutter + 4.dp),
                )
            }
            HuePicker(selected = hue, onSelect = { hue = it ?: hue }, allowLight = false)
            if (RoleRightGroups.any { (_, list) -> list.any { !offered(it) } }) Hint(R.string.manage_rights_lacking)
            RoleRightGroups.forEach { (group, list) ->
                SectionHeader(stringResource(group))
                Card {
                    list.forEach { right ->
                        RightToggle(right, right in rights, enabled = offered(right)) { on -> if (on) rights += right else rights -= right }
                    }
                }
            }
        }
        Button(
            text = stringResource(R.string.manage_role_save),
            onClick = {
                val saved = role.copy(title = title.text.toString().trim().ifBlank { untitled }, hue = hue, rights = rights.toSet())
                env.submit(GroupChange.SaveRole(saved))
                onClose()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        if (exists) {
            Button(
                text = stringResource(R.string.manage_role_delete),
                kind = ButtonKind.Secondary,
                icon = BleskIcons.Trash,
                onClick = {
                    env.submit(GroupChange.DeleteRole(role.id))
                    onClose()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

private val EXPIRIES = listOf(3_600_000L, 86_400_000L, 604_800_000L, null)
private val LIMITS = listOf(1, 5, 10, 100, null)
private val QrMargin = 32.dp
private const val ROLE_ID = 8
