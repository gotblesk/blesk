package app.blesk.core.design.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.R
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.shared.protocol.UsernameProblem
import app.blesk.shared.protocol.Usernames
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The lines under a @username field: what is known about the name, why something typed
 * vanished, and the rules. TalkBack reads a verdict out as it changes. A name nobody can take is
 * marked with an icon besides the colour and refused by vibration, once per name. [unknownText]
 * says what happens when the server could not be asked.
 */
@Composable
fun UsernameNote(status: UsernameStatus, notice: TypedNotice?, unknownText: String, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    var lastRefused by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(status) {
        if (refuseOn(status, lastRefused)) {
            haptics.refuse()
            lastRefused = refusedName(status)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Verdict(status, unknownText)
        if (notice != null) {
            Text(
                text = stringResource(
                    when (notice) {
                        TypedNotice.LatinOnly -> R.string.design_username_latin_only
                        TypedNotice.TooLong -> R.string.design_username_too_long
                    },
                ),
                style = Blesk.type.label,
                color = Blesk.colors.textSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(stringResource(R.string.design_username_rules), style = Blesk.type.caption, color = Blesk.colors.textTertiary)
    }
}

@Composable
private fun Verdict(status: UsernameStatus, unknownText: String) {
    val colors = Blesk.colors
    when (status) {
        UsernameStatus.Empty -> Unit
        // Not read out: it would chatter on every pause in typing.
        UsernameStatus.Checking ->
            Text(stringResource(R.string.design_username_checking), style = Blesk.type.label, color = colors.textTertiary)
        UsernameStatus.Yours -> VerdictLine(stringResource(R.string.design_username_yours), colors.textSecondary)
        UsernameStatus.Unknown -> VerdictLine(unknownText, colors.textSecondary)
        is UsernameStatus.Problem -> VerdictLine(stringResource(status.problem.text()), colors.textSecondary)
        is UsernameStatus.Free ->
            VerdictLine(stringResource(R.string.design_username_free, status.name), colors.textSecondary, BleskIcons.Check, colors.accent)
        is UsernameStatus.Taken ->
            VerdictLine(stringResource(R.string.design_username_taken, status.name), colors.danger, BleskIcons.Alert, colors.danger)
        is UsernameStatus.Reserved -> {
            val reason = if (Usernames.problem(status.name) == UsernameProblem.BotName) {
                R.string.design_username_bot
            } else {
                R.string.design_username_reserved
            }
            VerdictLine(stringResource(reason), colors.danger, BleskIcons.Alert, colors.danger)
        }
    }
}

/** A verdict, read out politely by TalkBack whenever it changes. Its icon is one line tall and grows with the text. */
@Composable
private fun VerdictLine(text: String, color: Color, icon: ImageVector? = null, tint: Color = color) {
    val style = Blesk.type.label
    Row(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, size = with(LocalDensity.current) { style.lineHeight.toDp() })
        Text(text, style = style, color = color)
    }
}

private fun UsernameProblem.text(): Int = when (this) {
    UsernameProblem.Characters -> R.string.design_username_characters
    UsernameProblem.TooShort -> R.string.design_username_too_short
    UsernameProblem.TooLong -> R.string.design_username_too_long
    UsernameProblem.StartsWithNonLetter -> R.string.design_username_starts_with_letter
    UsernameProblem.EdgeSeparator -> R.string.design_username_edge_separator
    UsernameProblem.DoubleSeparator -> R.string.design_username_double_separator
    UsernameProblem.BotName -> R.string.design_username_bot
    UsernameProblem.Reserved -> R.string.design_username_reserved
}

/**
 * Free names like a taken or kept-back one, offered by the server. A tap puts the name in the
 * field; it is already known to be free, so nobody is asked again.
 */
@Composable
fun UsernameSuggestions(status: UsernameStatus, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val offered = when (status) {
        is UsernameStatus.Taken -> status.suggestions
        is UsernameStatus.Reserved -> status.suggestions
        else -> persistentListOf()
    }
    val motion = Blesk.motion
    AnimatedContent(
        targetState = offered,
        modifier = modifier,
        transitionSpec = {
            val fade = fadeIn(motion.transition()) togetherWith fadeOut(motion.transition())
            fade using SizeTransform(clip = true) { _, _ -> motion.settle() }
        },
        // Other names in place of the offered ones only change the capsules; the block itself comes and goes with the offer.
        contentKey = { it.isEmpty() },
        label = "usernameSuggestions",
    ) { names ->
        if (names.isNotEmpty()) Offered(names, onPick)
    }
}

@Composable
private fun Offered(names: ImmutableList<String>, onPick: (String) -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.design_username_suggestions), style = Blesk.type.label, color = Blesk.colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEach { name ->
                val take = stringResource(R.string.design_username_take, name)
                Box(
                    Modifier
                        .heightIn(min = Blesk.dimens.touchTarget)
                        .clip(CircleShape)
                        .background(Blesk.colors.surfaceStrong)
                        .clickable(role = Role.Button) {
                            haptics.tick()
                            onPick(name)
                        }
                        .semantics { contentDescription = take }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("@$name", maxLines = 1)
                }
            }
        }
    }
}
