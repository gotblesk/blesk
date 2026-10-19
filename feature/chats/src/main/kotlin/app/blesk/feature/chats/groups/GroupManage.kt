package app.blesk.feature.chats.groups

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.Right

/** The pages under «Управление». */
internal enum class ManagePage { Members, Admins, MemberRights, Invites, Roles, Journal, Banned, Transfer, Delete }

/**
 * Asks the owner to prove it is them before something that cannot be undone: the app's code or
 * fingerprint when the lock is on. Shown over everything; exactly one of the two callbacks runs.
 */
typealias IdentityGate = @Composable (onPassed: () -> Unit, onCancel: () -> Unit) -> Unit

/** What the management pages work with. */
internal class ManageEnv(
    val state: GroupState,
    val me: String,
    val nameOf: (String) -> String,
    /** Makes a change; false when it was not allowed or did not go through. */
    val change: suspend (GroupChange) -> Boolean,
    val run: (suspend () -> Unit) -> Unit,
    val inviteLink: ((code: String) -> String)?,
    val gate: IdentityGate?,
    val onOpenPerson: (String) -> Unit,
    val onGone: () -> Unit,
) {
    fun can(right: Right) = GroupPolicy.can(state, me, right)

    val owner get() = state.roleOf(me) == GroupRole.Owner
    val room get() = state.kind == GroupKind.Room
}

/**
 * Below the details, for owners and admins: one row per thing to manage, with how many there are
 * on the right. Only what [me] may do is listed.
 */
@Composable
internal fun ManageSection(env: ManageEnv, now: Long, onOpen: (ManagePage) -> Unit) {
    val state = env.state
    if (!state.isAdmin(env.me) && !env.can(Right.Roles) && !env.can(Right.Ban)) return
    SectionHeader(stringResource(R.string.manage_title))
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        @Composable
        fun row(page: ManagePage, title: Int, count: Int? = null) {
            ListRow(
                title = stringResource(title),
                icon = page.icon,
                onClick = { onOpen(page) },
                trailing = count?.let { { Text("$it", style = Blesk.type.body, color = Blesk.colors.textSecondary) } },
            )
        }
        row(ManagePage.Members, R.string.manage_members, state.members.size)
        row(ManagePage.Admins, R.string.manage_admins, state.members.count { it.role == GroupRole.Admin })
        if (env.owner || env.can(Right.EditInfo)) row(ManagePage.MemberRights, R.string.manage_member_rights)
        if (env.can(Right.Invite)) row(ManagePage.Invites, R.string.manage_invites, state.invites.count { it.usable(now) })
        if (env.room && env.can(Right.Roles)) row(ManagePage.Roles, R.string.manage_roles, state.roles.size)
        if (env.can(Right.Journal)) row(ManagePage.Journal, R.string.manage_journal)
        if (env.can(Right.Ban)) row(ManagePage.Banned, R.string.manage_banned, state.banned.size)
        if (env.owner) {
            row(ManagePage.Transfer, R.string.manage_transfer)
            row(ManagePage.Delete, R.string.manage_delete)
        }
    }
}

private val ManagePage.icon
    get() = when (this) {
        ManagePage.Members -> BleskIcons.People
        ManagePage.Admins -> BleskIcons.Shield
        ManagePage.MemberRights -> BleskIcons.Check
        ManagePage.Invites -> BleskIcons.Invite
        ManagePage.Roles -> BleskIcons.Spark
        ManagePage.Journal -> BleskIcons.Clock
        ManagePage.Banned -> BleskIcons.Lock
        ManagePage.Transfer -> BleskIcons.Forward
        ManagePage.Delete -> BleskIcons.Trash
    }

private val ManagePage.title
    get() = when (this) {
        ManagePage.Members -> R.string.manage_members
        ManagePage.Admins -> R.string.manage_admins
        ManagePage.MemberRights -> R.string.manage_member_rights
        ManagePage.Invites -> R.string.manage_invites
        ManagePage.Roles -> R.string.manage_roles
        ManagePage.Journal -> R.string.manage_journal
        ManagePage.Banned -> R.string.manage_banned
        ManagePage.Transfer -> R.string.manage_transfer
        ManagePage.Delete -> R.string.manage_delete
    }

/** One management page over the info screen; «назад» returns to it. */
@Composable
internal fun ManageScreen(page: ManagePage, env: ManageEnv, now: Long, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var gating by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Without a lock there is nothing more to prove than the slider already did.
    val guarded: (() -> Unit) -> Unit = { action -> if (env.gate == null) action() else gating = action }
    Box(Modifier.fillMaxSize().background(Blesk.colors.background)) {
        ScrollScreen(title = stringResource(page.title), onBack = onBack) {
            when (page) {
                ManagePage.Members -> MembersPage(env)
                ManagePage.Admins -> AdminsPage(env)
                ManagePage.MemberRights -> MemberRightsPage(env)
                ManagePage.Invites -> InvitesPage(env, now)
                ManagePage.Roles -> RolesPage(env)
                ManagePage.Journal -> JournalPage(env)
                ManagePage.Banned -> BannedPage(env)
                ManagePage.Transfer -> TransferPage(env, guarded, onBack)
                ManagePage.Delete -> DeletePage(env, guarded)
            }
        }
        val pending = gating
        if (pending != null && env.gate != null) {
            env.gate.invoke(
                {
                    gating = null
                    pending()
                },
                { gating = null },
            )
        }
    }
}
