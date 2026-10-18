package app.blesk.feature.settings

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.QrCode
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.SkeletonRows
import app.blesk.core.design.component.StateMessage
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.rememberCopy
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.shared.protocol.InviteInfo
import app.blesk.shared.protocol.InviteList
import app.blesk.shared.protocol.Invites
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.blesk.core.design.R as DesignR

/** How «Создать приглашение» went: the new code, or why there is none. */
sealed interface InviteCreation {
    data class Made(val code: String) : InviteCreation

    /** Every invite that may be held at once is out and still unused; the list then shows it. */
    data object Limit : InviteCreation
    data object TooOften : InviteCreation
    data object Failed : InviteCreation
}

/**
 * «Ты → Приглашения»: how many invites can still be made, the ones given out and who came by them.
 * An unused invite opens into its link to send or copy and a QR code to show from the screen.
 */
@Composable
fun InvitesScreen(
    /** Null while the list is on its way from the server. */
    invites: InviteList?,
    onCreate: suspend () -> InviteCreation,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
    onRetry: () -> Unit = {},
    /** The invite shown open with its link and actions. */
    opened: String? = null,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var open by rememberSaveable { mutableStateOf(opened) }
    var busy by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf<InviteCreation?>(null) }
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp)
    ScrollScreen(title = stringResource(R.string.you_invites), onBack = onBack, modifier = modifier) {
        Text(
            text = pluralStringResource(R.plurals.invites_intro, Invites.TTL_DAYS, Invites.TTL_DAYS),
            color = Blesk.colors.textSecondary,
            modifier = pad,
        )
        when {
            failed -> StateMessage(
                text = stringResource(R.string.invites_failed),
                action = stringResource(DesignR.string.design_retry),
                onAction = onRetry,
            )
            invites == null -> SkeletonRows(count = 3, avatar = 40.dp)
            else -> {
                Allowance(
                    left = invites.left,
                    refused = refused,
                    onCreate = {
                        if (busy) return@Allowance
                        busy = true
                        refused = null
                        scope.launch {
                            when (val made = onCreate()) {
                                is InviteCreation.Made -> {
                                    haptics.success()
                                    open = made.code
                                }
                                else -> {
                                    haptics.refuse()
                                    refused = made
                                }
                            }
                            busy = false
                        }
                    },
                    onRefuse = { haptics.refuse() },
                )
                GivenOut(invites.invites, open, onOpen = { code -> open = code.takeIf { it != open } })
            }
        }
    }
}

/** How many more invites can be made, and the button that makes one; dimmed, with the reason, at the limit. */
@Composable
private fun Allowance(left: Int, refused: InviteCreation?, onCreate: () -> Unit, onRefuse: () -> Unit) {
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp)
    val atLimit = left <= 0
    Text(
        text = if (atLimit) {
            stringResource(R.string.invites_none_left)
        } else {
            pluralStringResource(R.plurals.invites_left, left, left, Invites.LIMIT)
        },
        style = Blesk.type.headline,
        modifier = pad.padding(top = 20.dp),
    )
    if (atLimit) {
        Text(
            text = stringResource(R.string.invites_none_left_hint),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = pad.padding(top = 4.dp),
        )
    }
    val label = stringResource(R.string.invites_create)
    Button(
        text = label,
        icon = BleskIcons.Plus,
        onClick = if (atLimit) onRefuse else onCreate,
        modifier = Modifier
            .padding(Blesk.dimens.gutter)
            .fillMaxWidth()
            .then(
                if (atLimit) {
                    Modifier.alpha(DIMMED).clearAndSetSemantics {
                        contentDescription = label
                        role = Role.Button
                        disabled()
                    }
                } else {
                    Modifier
                },
            ),
    )
    val problem = when (refused) {
        InviteCreation.Failed -> R.string.invites_create_failed
        InviteCreation.TooOften -> R.string.invites_too_often
        else -> null
    }
    if (problem != null) {
        Text(
            text = stringResource(problem),
            style = Blesk.type.label,
            color = Blesk.colors.danger,
            modifier = pad.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The invites given out, newest first: open ones to send, used ones with who came by them. */
@Composable
private fun GivenOut(invites: List<InviteInfo>, open: String?, onOpen: (String) -> Unit) {
    SectionHeader(stringResource(R.string.invites_mine))
    if (invites.isEmpty()) {
        Text(
            text = stringResource(R.string.invites_empty),
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp),
        )
        return
    }
    val until = remember { DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru")) }
    val day = remember { DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")) }
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        invites.forEach { invite ->
            val usedBy = invite.usedBy
            if (usedBy != null) {
                ListRow(
                    title = stringResource(R.string.invites_used_by, usedBy),
                    subtitle = stringResource(R.string.invites_used_on, day.at(invite.usedAtMillis ?: invite.createdAtMillis)),
                    icon = BleskIcons.Person,
                    showChevron = false,
                )
            } else {
                ListRow(
                    title = Invites.link(invite.code).removePrefix("https://"),
                    subtitle = stringResource(R.string.invites_until, until.at(invite.expiresAtMillis)),
                    icon = BleskIcons.Invite,
                    onClick = { onOpen(invite.code) },
                )
                AnimatedVisibility(open == invite.code) { InviteTools(invite.code) }
            }
        }
    }
}

/** Sending, copying and showing one invite. */
@Composable
private fun InviteTools(code: String) {
    val link = Invites.link(code)
    val context = LocalContext.current
    val copy = rememberCopy()
    var qr by rememberSaveable(code) { mutableStateOf(false) }
    val text = stringResource(R.string.invites_share_text, link)
    val chooser = stringResource(R.string.invites_share_title)
    Column(
        Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            text = stringResource(R.string.invites_share),
            icon = BleskIcons.Share,
            kind = ButtonKind.Secondary,
            onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, chooser))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // Side by side while they fit; with a large font each takes its own line.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                text = stringResource(R.string.invites_copy),
                icon = BleskIcons.Copy,
                kind = ButtonKind.Secondary,
                onClick = { copy(link, false) },
            )
            Button(
                text = stringResource(if (qr) R.string.invites_qr_hide else R.string.invites_qr),
                icon = BleskIcons.Qr,
                kind = ButtonKind.Secondary,
                onClick = { qr = !qr },
            )
        }
        AnimatedVisibility(qr) {
            val label = stringResource(R.string.invites_qr_label)
            // Dark modules on white whatever the theme: many scanners cannot read inverted codes.
            Box(
                Modifier
                    .padding(horizontal = Blesk.dimens.gutter * 2, vertical = 4.dp)
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(Blesk.dimens.cardRadius))
                    .padding(20.dp)
                    .semantics { contentDescription = label },
            ) {
                QrCode(link, Color.Black, Modifier.fillMaxWidth().aspectRatio(1f))
            }
        }
    }
}

private fun DateTimeFormatter.at(millis: Long): String = format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private const val DIMMED = 0.4f
