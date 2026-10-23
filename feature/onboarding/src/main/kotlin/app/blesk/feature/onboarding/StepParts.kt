package app.blesk.feature.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.coroutines.delay

/**
 * What an entry step is doing, or why it stopped. [busyText] appears only once the work has gone
 * on for a moment, so a quick answer never flashes it. A [problem] is marked with an icon besides
 * its colour, read out by TalkBack and refused by vibration each time it comes back; with
 * [onUpdate], an outdated app gets «Обновить» under it.
 */
@Composable
internal fun StatusLine(
    busy: Boolean,
    busyText: String,
    problem: AccessProblem?,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    onUpdate: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        waited = false
        if (!busy) return@LaunchedEffect
        delay(BUSY_SHOWN_AFTER_MILLIS)
        waited = true
    }
    // Hidden while a new attempt runs, so the same problem coming back is refused again.
    val shown = problem.takeIf { !busy }
    LaunchedEffect(shown) { if (shown != null) haptics.refuse() }
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize(Blesk.motion.settle())
            .padding(horizontal = Blesk.dimens.gutter + 4.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        when {
            busy && waited -> Text(
                text = busyText,
                style = Blesk.type.label,
                color = Blesk.colors.textSecondary,
                textAlign = align,
                modifier = Modifier.padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            shown != null -> {
                ProblemLine(shown.text(), align, Modifier.padding(vertical = 8.dp))
                if (shown == AccessProblem.AppTooOld && onUpdate != null) {
                    Button(stringResource(R.string.onboarding_update), onClick = onUpdate, kind = ButtonKind.Secondary)
                }
            }
        }
    }
}

/**
 * A refusal in the colour of danger, never only in it: an icon stands before the first word and
 * flows with the text, so it stays beside it however the lines are aligned.
 */
@Composable
internal fun ProblemLine(text: String, align: TextAlign, modifier: Modifier = Modifier) {
    val danger = Blesk.colors.danger
    val line = buildAnnotatedString {
        appendInlineContent(ALERT, "!")
        append(" ")
        append(text)
    }
    val icon = InlineTextContent(Placeholder(IconSize, IconSize, PlaceholderVerticalAlign.TextCenter)) {
        Icon(BleskIcons.Alert, contentDescription = null, tint = danger, modifier = Modifier.fillMaxSize())
    }
    BasicText(
        text = line,
        style = Blesk.type.label.merge(color = danger, textAlign = align),
        inlineContent = mapOf(ALERT to icon),
        // Read as the words alone: the icon only repeats what the colour says.
        modifier = modifier.clearAndSetSemantics {
            this.text = AnnotatedString(text)
            liveRegion = LiveRegionMode.Polite
        },
    )
}

/** A small link-like row, such as «Другой сервер»: quiet, but a full touch target. [action] follows the text in a darker ink. */
@Composable
internal fun LinkRow(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, action: String? = null) {
    Row(
        modifier = modifier
            .heightIn(min = Blesk.dimens.touchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val style = Blesk.type.label
        val iconSize = with(LocalDensity.current) { style.lineHeight.toDp() }
        Icon(icon, contentDescription = null, tint = Blesk.colors.textSecondary, size = iconSize)
        Text(text, style = style, color = Blesk.colors.textSecondary, modifier = Modifier.weight(1f, fill = false))
        if (action != null) Text(action, style = Blesk.type.label, color = Blesk.colors.textPrimary)
    }
}

private const val ALERT = "alert"
private val IconSize = 1.2.em

/** Under this long, work is quick enough to need no words. */
private const val BUSY_SHOWN_AFTER_MILLIS = 300L
