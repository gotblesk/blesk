package app.blesk.feature.chats.groups

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blesk.core.design.color.ContrastLevel
import app.blesk.core.design.color.ensureContrast
import app.blesk.core.design.color.oklch
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.Right

@get:StringRes
internal val Right.label: Int
    get() = when (this) {
        Right.Write -> R.string.right_write
        Right.Media -> R.string.right_media
        Right.Stickers -> R.string.right_stickers
        Right.Links -> R.string.right_links
        Right.Polls -> R.string.right_polls
        Right.Invite -> R.string.right_invite
        Right.Pin -> R.string.right_pin
        Right.EditInfo -> R.string.right_edit_info
        Right.DeleteOthers -> R.string.right_delete_others
        Right.Ban -> R.string.right_ban
        Right.Calls -> R.string.right_calls
        Right.AddAdmins -> R.string.right_add_admins
        Right.Channels -> R.string.right_channels
        Right.Roles -> R.string.right_roles
        Right.Journal -> R.string.right_journal
        Right.Timeout -> R.string.right_timeout
        Right.AssignRoles -> R.string.right_assign_roles
        Right.MentionAll -> R.string.right_mention_all
        Right.Connect -> R.string.right_connect
        Right.Speak -> R.string.right_speak
        Right.Screen -> R.string.right_screen
        Right.MuteOthers -> R.string.right_mute_others
        Right.Move -> R.string.right_move
    }

/** One line of plain words on what the right gives. */
@get:StringRes
internal val Right.hint: Int
    get() = when (this) {
        Right.Write -> R.string.right_write_hint
        Right.Media -> R.string.right_media_hint
        Right.Stickers -> R.string.right_stickers_hint
        Right.Links -> R.string.right_links_hint
        Right.Polls -> R.string.right_polls_hint
        Right.Invite -> R.string.right_invite_hint
        Right.Pin -> R.string.right_pin_hint
        Right.EditInfo -> R.string.right_edit_info_hint
        Right.DeleteOthers -> R.string.right_delete_others_hint
        Right.Ban -> R.string.right_ban_hint
        Right.Calls -> R.string.right_calls_hint
        Right.AddAdmins -> R.string.right_add_admins_hint
        Right.Channels -> R.string.right_channels_hint
        Right.Roles -> R.string.right_roles_hint
        Right.Journal -> R.string.right_journal_hint
        Right.Timeout -> R.string.right_timeout_hint
        Right.AssignRoles -> R.string.right_assign_roles_hint
        Right.MentionAll -> R.string.right_mention_all_hint
        Right.Connect -> R.string.right_connect_hint
        Right.Speak -> R.string.right_speak_hint
        Right.Screen -> R.string.right_screen_hint
        Right.MuteOthers -> R.string.right_mute_others_hint
        Right.Move -> R.string.right_move_hint
    }

/** What every member of a group may be allowed; rooms add their voice rights. */
internal fun memberRightsOf(kind: GroupKind): List<Right> = when (kind) {
    GroupKind.Group -> listOf(Right.Write, Right.Media, Right.Stickers, Right.Links, Right.Polls, Right.Invite, Right.Pin, Right.EditInfo)
    GroupKind.Room -> listOf(
        Right.Write, Right.Media, Right.Stickers, Right.Links, Right.Invite, Right.Pin, Right.Connect, Right.Speak, Right.Screen,
    )
}

/** What an admin may be given. */
internal fun adminRightsOf(kind: GroupKind): List<Right> = when (kind) {
    GroupKind.Group -> listOf(Right.EditInfo, Right.DeleteOthers, Right.Ban, Right.Invite, Right.Pin, Right.Calls, Right.AddAdmins)
    GroupKind.Room -> Right.ADMIN_ALL.sortedBy { it.ordinal }
}

/** A room role's rights, in the four groups the editor shows them in. */
internal val RoleRightGroups: List<Pair<Int, List<Right>>> = listOf(
    R.string.manage_role_group_room to listOf(Right.EditInfo, Right.Channels, Right.Roles, Right.Invite, Right.Journal),
    R.string.manage_role_group_people to listOf(Right.Ban, Right.Timeout, Right.AssignRoles),
    R.string.manage_role_group_messages to listOf(Right.Write, Right.DeleteOthers, Right.Pin, Right.MentionAll),
    R.string.manage_role_group_voice to listOf(Right.Connect, Right.Speak, Right.Screen, Right.MuteOthers, Right.Move),
)

@Composable
internal fun RightToggle(right: Right, checked: Boolean, onChange: (Boolean) -> Unit) {
    ToggleRow(title = stringResource(right.label), subtitle = stringResource(right.hint), checked = checked, onCheckedChange = onChange)
}

/** The eight hues a community or a role may take: the palette's own, evenly round the wheel. */
const val COMMUNITY_HUES = 8

/** A hue as a dot of light, readable on the background. */
@Composable
internal fun rememberHueInk(hue: Int): Color {
    val dark = Blesk.colors.isDark
    val background = Blesk.colors.background
    return remember(hue, dark, background) {
        val degrees = hue.mod(COMMUNITY_HUES) * (360f / COMMUNITY_HUES) + HUE_SHIFT
        val ink = oklch(if (dark) HUE_DARK_L else HUE_LIGHT_L, HUE_CHROMA, degrees)
        ensureContrast(ink, background, ContrastLevel.UI_COMPONENT)
    }
}

/** A small dot of a role's or community's hue, next to a name. */
@Composable
internal fun HueDot(hue: Int, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).background(rememberHueInk(hue), CircleShape))
}

/** Eight dots, and «Свет» first when [allowLight]; the chosen one ringed. Null is «Свет». */
@Composable
internal fun HuePicker(selected: Int?, onSelect: (Int?) -> Unit, allowLight: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val options = (if (allowLight) listOf<Int?>(null) else emptyList()) + (0 until COMMUNITY_HUES).toList()
        options.forEach { hue ->
            val label = if (hue == null) stringResource(R.string.manage_hue_light) else stringResource(R.string.manage_hue, hue + 1)
            val ring = if (hue == selected) Blesk.colors.textPrimary else Color.Transparent
            Box(
                Modifier
                    .size(Blesk.dimens.touchTarget)
                    .selectable(selected = hue == selected, role = Role.RadioButton) { onSelect(hue) }
                    .semantics { contentDescription = label },
            ) {
                Box(
                    Modifier
                        .padding(8.dp)
                        .size(32.dp)
                        .border(2.dp, ring, CircleShape)
                        .padding(4.dp)
                        .background(if (hue == null) Blesk.colors.light else rememberHueInk(hue), CircleShape),
                )
            }
        }
    }
}

private const val HUE_LIGHT_L = 0.62f
private const val HUE_DARK_L = 0.78f
private const val HUE_CHROMA = 0.14f
private const val HUE_SHIFT = 20f
