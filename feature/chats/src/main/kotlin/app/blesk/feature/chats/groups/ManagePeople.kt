package app.blesk.feature.chats.groups

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Choice
import app.blesk.core.design.component.ChoiceSheet
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.MenuSheet
import app.blesk.core.design.component.SlideToConfirm
import app.blesk.core.design.component.Tag
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.LogAction
import app.blesk.shared.protocol.Right
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** A member with their place: a capsule for owner and admin, dots of their room roles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MemberLine(
    env: ManageEnv,
    member: GroupMember,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    online: Boolean = false,
    you: String? = null,
) {
    val haptics = rememberHaptics()
    val name = env.nameOf(member.username).let { if (you != null) "$it · $you" else it }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = onClick != null || onLongClick != null,
                role = Role.Button,
                onLongClickLabel = onLongClick?.let { stringResource(R.string.group_manage) },
                onLongClick = onLongClick?.let {
                    {
                        haptics.menu()
                        it()
                    }
                },
                onClick = { onClick?.invoke() },
            )
            .padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(name, size = 40.dp, key = member.username, online = online)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = Blesk.type.bodyStrong, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                env.state.roles.filter { it.id in member.roles }.forEach { HueDot(it.hue) }
            }
            Text("@${member.username}", style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
        }
        RoleCapsule(member.role)
    }
}

@Composable
internal fun RoleCapsule(role: GroupRole) {
    when (role) {
        GroupRole.Owner -> Tag(stringResource(R.string.group_role_owner))
        GroupRole.Admin -> Tag(stringResource(R.string.group_role_admin))
        GroupRole.Member -> Unit
    }
}

@Composable
internal fun MembersPage(env: ManageEnv, onSheet: (ManageSheet) -> Unit) {
    val query = rememberTextFieldState()
    TextInput(label = stringResource(R.string.group_search), state = query)
    MemberList(env, query.text.toString().trim(), onSheet = onSheet)
}

/**
 * Members by seniority. A long press on someone below you opens [ManageSheet.Member], what you may do to them:
 * their rights, their roles, «Исключить», «Заблокировать здесь».
 */
@Composable
internal fun MemberList(env: ManageEnv, needle: String, online: Set<String> = emptySet(), onSheet: (ManageSheet) -> Unit) {
    val you = stringResource(R.string.group_you)
    val shown = env.state.members
        .filter { needle.isEmpty() || env.nameOf(it.username).contains(needle, true) || it.username.contains(needle, true) }
        .sortedBy { GroupPolicy.rank(env.state, it.username) }
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        if (shown.isEmpty()) Empty(R.string.manage_nobody)
        shown.forEach { member ->
            val mine = member.username == env.me
            val below = GroupPolicy.rank(env.state, member.username) > GroupPolicy.rank(env.state, env.me)
            val manageable = GroupPolicy.appoints(env.state, env.me, member.username) || env.can(Right.Ban) || givesRoles(env)
            MemberLine(
                env,
                member,
                onClick = if (mine) null else ({ env.onOpenPerson(member.username) }),
                onLongClick = if (mine || !below || !manageable) null else ({ onSheet(ManageSheet.Member(member.username)) }),
                online = member.username in online,
                you = if (mine) you else null,
            )
        }
    }
}

/** Whether there is a role [ManageEnv.me] may give: the right alone is not enough without a role below them. */
private fun givesRoles(env: ManageEnv) =
    env.room && env.can(Right.AssignRoles) && env.state.roles.any { GroupPolicy.outranks(env.state, env.me, it.id) }

/** What [ManageEnv.me] may do to [username]; each choice that opens another sheet passes it to [onSheet]. */
@Composable
internal fun MemberMenu(env: ManageEnv, username: String, onSheet: (ManageSheet?) -> Unit) {
    ChoiceSheet(
        title = stringResource(R.string.manage_member_menu, env.nameOf(username)),
        choices = buildList {
            if (GroupPolicy.appoints(env.state, env.me, username)) {
                add(Choice(stringResource(R.string.manage_member_rights_action)) { onSheet(ManageSheet.AdminRights(username)) })
            }
            if (givesRoles(env)) {
                add(Choice(stringResource(R.string.manage_member_roles)) { onSheet(ManageSheet.MemberRoles(username)) })
            }
            if (env.can(Right.Ban)) {
                val remove = GroupChange.Remove(username)
                val ban = GroupChange.Ban(username)
                add(Choice(stringResource(R.string.manage_member_remove), danger = true) { env.submit(remove) })
                add(Choice(stringResource(R.string.manage_member_ban), danger = true) { env.submit(ban) })
            }
        },
        onDismiss = { onSheet(null) },
    )
}

@Composable
internal fun AdminsPage(env: ManageEnv, onSheet: (ManageSheet) -> Unit) {
    val admins = env.state.members.filter { it.role != GroupRole.Member }.sortedBy { it.role.ordinal }
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        admins.forEach { member ->
            val editable = member.role == GroupRole.Admin && appoints(env, member)
            MemberLine(env, member, onClick = if (editable) ({ onSheet(ManageSheet.AdminRights(member.username)) }) else null)
        }
        if (admins.none { it.role == GroupRole.Admin }) Empty(R.string.manage_admins_empty)
    }
    if (adminCandidates(env).isNotEmpty()) {
        Button(
            text = stringResource(R.string.manage_admin_add),
            kind = ButtonKind.Secondary,
            onClick = { onSheet(ManageSheet.PickAdmin) },
            modifier = Modifier.padding(Blesk.dimens.gutter),
        )
    }
}

/** Admins stand level: only the owner changes another admin. */
private fun appoints(env: ManageEnv, member: GroupMember) =
    member.username != env.me && GroupPolicy.appoints(env.state, env.me, member.username)

private fun adminCandidates(env: ManageEnv) = env.state.members.filter { it.role == GroupRole.Member && appoints(env, it) }

/** The members [ManageEnv.me] may make admins; picking one opens their rights. */
@Composable
internal fun AdminPicker(env: ManageEnv, onSheet: (ManageSheet?) -> Unit) {
    MenuSheet(title = stringResource(R.string.manage_admin_pick), onDismiss = { onSheet(null) }) {
        Card {
            Column(Modifier.heightIn(max = SheetMax).verticalScroll(rememberScrollState())) {
                adminCandidates(env).forEach { member ->
                    MemberLine(env, member, onClick = { onSheet(ManageSheet.AdminRights(member.username)) })
                }
            }
        }
    }
}

/**
 * The rights of one admin, as switches with a line each. A right the editor lacks stays as the admin has it: switched
 * off, unless the admin had it already.
 */
@Composable
internal fun AdminEditor(env: ManageEnv, username: String, onClose: () -> Unit) {
    val member = env.state.members.firstOrNull { it.username == username } ?: return
    val had = if (member.role == GroupRole.Admin) member.rights ?: Right.ADMIN_ALL else emptySet()
    val offered = adminRightsOf(env.state.kind)
    fun givable(right: Right) = right in had || env.can(right)
    val chosen = remember(username) {
        mutableStateListOf<Right>().apply {
            addAll(if (member.role == GroupRole.Admin) had else offered.filter { givable(it) && it != Right.AddAdmins })
        }
    }
    MenuSheet(title = env.nameOf(username), onDismiss = onClose) {
        Card {
            Column(Modifier.heightIn(max = SheetMax).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.manage_admin_rights_hint),
                    style = Blesk.type.caption,
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp),
                )
                offered.forEach { right ->
                    RightToggle(right, right in chosen, enabled = givable(right)) { on -> if (on) chosen += right else chosen -= right }
                }
            }
        }
        Button(
            text = stringResource(R.string.manage_admin_save),
            onClick = {
                env.submit(GroupChange.Admin(username, chosen.toSet()))
                onClose()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        if (member.role == GroupRole.Admin) {
            Button(
                text = stringResource(R.string.manage_admin_drop),
                kind = ButtonKind.Secondary,
                onClick = {
                    env.submit(GroupChange.Admin(username, null))
                    onClose()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/** The roles below yours, to give or take; one bringing a right or a channel you lack is shown but cannot be given. */
@Composable
internal fun MemberRolesSheet(env: ManageEnv, username: String, onClose: () -> Unit) {
    val member = env.state.members.firstOrNull { it.username == username } ?: return
    // Roles at or above your own are not yours to give.
    val roles = env.state.roles.filter { GroupPolicy.outranks(env.state, env.me, it.id) }
    val untitled = stringResource(R.string.manage_role_untitled)
    MenuSheet(title = env.nameOf(username), onDismiss = onClose) {
        Column(Modifier.heightIn(max = SheetMax).verticalScroll(rememberScrollState())) {
            val locked = roles.filter { !env.allows(GroupChange.AssignRole(username, it.id, it.id !in member.roles)) }
            if (locked.isNotEmpty()) Hint(R.string.manage_member_roles_lacking)
            Card {
                roles.forEach { role ->
                    ToggleRow(
                        title = role.title.ifBlank { untitled },
                        checked = role.id in member.roles,
                        enabled = role !in locked,
                        onCheckedChange = { on -> env.submit(GroupChange.AssignRole(username, role.id, on)) },
                    )
                }
            }
        }
    }
}

/** What every member may do; you switch on only what you may do yourself. */
@Composable
internal fun MemberRightsPage(env: ManageEnv) {
    Text(
        stringResource(R.string.manage_member_rights_hint),
        style = Blesk.type.caption,
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 8.dp),
    )
    val rights = memberRightsOf(env.state.kind)
    if (rights.any { !env.can(it) }) Hint(R.string.manage_rights_lacking)
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        rights.forEach { right ->
            RightToggle(right, right in env.state.memberRights, enabled = env.can(right)) { on ->
                env.submit(GroupChange.MemberRights(if (on) env.state.memberRights + right else env.state.memberRights - right))
            }
        }
    }
}

@Composable
internal fun BannedPage(env: ManageEnv) {
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        if (env.state.banned.isEmpty()) Empty(R.string.manage_banned_empty)
        env.state.banned.forEach { username ->
            ListRow(
                title = env.nameOf(username),
                subtitle = "@$username",
                showChevron = false,
                trailing = {
                    Button(
                        text = stringResource(R.string.manage_unban),
                        kind = ButtonKind.Secondary,
                        onClick = { env.submit(GroupChange.Unban(username)) },
                    )
                },
            )
        }
    }
}

@Composable
internal fun JournalPage(env: ManageEnv) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru")) }
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        if (env.state.log.isEmpty()) Empty(R.string.manage_journal_empty)
        env.state.log.asReversed().forEach { entry ->
            val target = when (entry.action) {
                LogAction.Role -> env.state.roles.firstOrNull { it.id == entry.target }?.title ?: env.nameOf(entry.target)
                else -> env.nameOf(entry.target)
            }
            val text = when (entry.action) {
                LogAction.Edited -> stringResource(R.string.log_edited)
                LogAction.Rights -> stringResource(R.string.log_rights)
                LogAction.Admin -> stringResource(R.string.log_admin, target)
                LogAction.Removed -> stringResource(R.string.log_removed, target)
                LogAction.Banned -> stringResource(R.string.log_banned, target)
                LogAction.Unbanned -> stringResource(R.string.log_unbanned, target)
                LogAction.Invite -> stringResource(R.string.log_invite, entry.target)
                LogAction.Revoked -> stringResource(R.string.log_revoked)
                LogAction.Owner -> stringResource(R.string.log_owner, target)
                LogAction.Channel -> stringResource(R.string.log_channel)
                LogAction.Role -> stringResource(R.string.log_role, target)
            }
            val at = formatter.format(Instant.ofEpochMilli(entry.at).atZone(ZoneId.systemDefault()))
            ListRow(title = text, subtitle = "${env.nameOf(entry.actor)} · $at", showChevron = false)
        }
    }
}

/**
 * The owner picks who takes over in [ManageSheet.Transfer], reads what follows, and slides to confirm (then the code
 * or a fingerprint, when the lock is on). Once [handed] over, the «владелец» capsule flows from their avatar to the
 * new owner's.
 */
@Composable
internal fun TransferPage(env: ManageEnv, handed: String?, onSheet: (ManageSheet) -> Unit, onDone: () -> Unit) {
    if (handed != null) {
        OwnerFlow(env, from = env.me, to = handed, onFinished = onDone)
        return
    }
    val candidates = env.state.members.filter { it.username != env.me }
    if (candidates.isEmpty()) {
        Empty(R.string.manage_transfer_nobody)
        return
    }
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        candidates.forEach { member ->
            MemberLine(env, member, onClick = { onSheet(ManageSheet.Transfer(member.username)) })
        }
    }
}

/** What handing the group to [username] means, and the slider that does it: [onConfirm]. */
@Composable
internal fun TransferSheet(env: ManageEnv, username: String, onClose: () -> Unit, onConfirm: () -> Unit) {
    MenuSheet(title = stringResource(R.string.manage_transfer), onDismiss = onClose) {
        Text(
            stringResource(R.string.manage_transfer_card, env.nameOf(username)),
            style = Blesk.type.body,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )
        SlideToConfirm(
            label = stringResource(R.string.manage_transfer_slide),
            actionLabel = stringResource(R.string.manage_transfer_slide),
            onConfirm = onConfirm,
        )
    }
}

/** Two avatars on a bridge; the owner's capsule slides along it from one to the other. */
@Composable
private fun OwnerFlow(env: ManageEnv, from: String, to: String, onFinished: () -> Unit) {
    val motion = Blesk.motion
    val haptics = rememberHaptics()
    val travel = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        travel.animateTo(1f, motion.stretch())
        haptics.success()
        delay(FLOW_HOLD_MILLIS)
        onFinished()
    }
    val density = LocalDensity.current
    Column(Modifier.fillMaxWidth().padding(Blesk.dimens.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(FlowHeight)) {
            val span = with(density) { (maxWidth - FlowAvatar).toPx() }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = FlowAvatar / 2)
                    .height(4.dp)
                    .background(Blesk.colors.surfaceStrong, CircleShape),
            )
            Avatar(env.nameOf(from), key = from, size = FlowAvatar, modifier = Modifier.align(Alignment.CenterStart))
            Avatar(env.nameOf(to), key = to, size = FlowAvatar, modifier = Modifier.align(Alignment.CenterEnd))
            Box(Modifier.align(Alignment.BottomStart).offset { IntOffset((span * travel.value).roundToInt(), 0) }) {
                Tag(stringResource(R.string.group_role_owner))
            }
        }
        Text(stringResource(R.string.manage_transfer_done), style = Blesk.type.headline, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
internal fun DeletePage(env: ManageEnv, guarded: (() -> Unit) -> Unit) {
    val state = env.state
    val title = if (state.kind == GroupKind.Room) R.string.manage_delete_room else R.string.manage_delete_group
    Column(Modifier.padding(Blesk.dimens.gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(title, state.title, state.members.size), style = Blesk.type.headline)
        Text(stringResource(R.string.manage_delete_hint), style = Blesk.type.body, color = Blesk.colors.textSecondary)
        SlideToConfirm(
            label = stringResource(R.string.manage_delete_slide),
            actionLabel = stringResource(R.string.manage_delete_slide),
            onConfirm = {
                guarded {
                    env.run { if (env.change(GroupChange.Dissolve)) env.onGone() }
                }
            },
        )
    }
}

/** Why some of what is below is switched off. */
@Composable
internal fun Hint(text: Int) {
    Text(
        stringResource(text),
        style = Blesk.type.caption,
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 8.dp),
    )
}

@Composable
internal fun Empty(text: Int) {
    Text(
        stringResource(text),
        style = Blesk.type.body,
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(Blesk.dimens.gutter),
    )
}

internal val SheetMax = 440.dp
private val FlowAvatar = 64.dp
private val FlowHeight = 96.dp
private const val FLOW_HOLD_MILLIS = 900L
