package app.blesk.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.CheckRow
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.fire.lightSweep
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk

/**
 * The offer of a code-password: a lock on this phone alone, not part of the account, and what
 * forgetting it costs. [onBack] is null where the step has nothing to go back to.
 */
@Composable
fun CodeOfferScreen(onEnable: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    ScrollScreen(
        title = stringResource(R.string.onboarding_code_title),
        onBack = onBack,
        modifier = modifier,
        bottom = {
            StepActions(
                primary = StepButton(stringResource(R.string.onboarding_code_on), onEnable),
                secondary = StepButton(stringResource(R.string.onboarding_code_later), onLater),
            )
        },
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(LockCircle).clip(CircleShape).background(Blesk.colors.surfaceStrong),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BleskIcons.Lock, contentDescription = null, tint = Blesk.colors.textSecondary, size = 32.dp)
            }
        }
        StepText(stringResource(R.string.onboarding_code_text))
    }
}

/**
 * The theme, chosen on a [preview] that changes with it: [picker] holds the themes, [preview] a
 * piece of a chat in the theme chosen now.
 */
@Composable
fun ThemeStepScreen(
    picker: @Composable () -> Unit,
    preview: @Composable () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    ScrollScreen(
        title = stringResource(R.string.onboarding_theme_title),
        onBack = onBack,
        modifier = modifier,
        bottom = { StepActions(StepButton(stringResource(R.string.onboarding_next), onNext)) },
    ) {
        StepText(stringResource(R.string.onboarding_theme_text))
        val shape = RoundedCornerShape(Blesk.dimens.cardRadius)
        // On the page's own background, as a chat lies: on a card the other side's bubbles would vanish.
        Box(
            Modifier
                .padding(horizontal = Blesk.dimens.gutter)
                .padding(bottom = 16.dp)
                .fillMaxWidth()
                .clip(shape)
                .border(Blesk.dimens.hairline, Blesk.colors.hairline, shape),
        ) { preview() }
        picker()
    }
}

/**
 * «Язык блеска»: what the glint means, shown on a protected chat that catches the light and a
 * matte channel, each also said in words. [channel] is whether the server has an official
 * channel to offer; [subscribe] ticks it.
 */
@Composable
fun GlintStepScreen(
    channel: Boolean,
    subscribe: Boolean,
    onSubscribeChange: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    ScrollScreen(
        title = stringResource(R.string.onboarding_glint_title),
        onBack = onBack,
        modifier = modifier,
        bottom = { StepActions(StepButton(stringResource(R.string.onboarding_glint_done), onDone)) },
    ) {
        Text(
            text = stringResource(R.string.onboarding_glint_line),
            style = Blesk.type.title,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).padding(top = 4.dp, bottom = 8.dp),
        )
        StepText(stringResource(R.string.onboarding_glint_text))
        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var entered by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { entered = true }
            Example(
                title = stringResource(R.string.onboarding_glint_private),
                note = stringResource(R.string.onboarding_glint_private_note),
                avatar = {
                    Avatar(it, protected = true, key = EXAMPLE_FACE, modifier = Modifier.lightSweep(entered, CircleShape))
                },
                mark = { Icon(BleskIcons.Lock, contentDescription = null, tint = Blesk.colors.textSecondary, size = 18.dp) },
            )
            // No mark here: the words under the title already say it, as the brief says it.
            Example(
                title = stringResource(R.string.onboarding_glint_public),
                note = stringResource(R.string.onboarding_glint_public_note),
                avatar = { Avatar(it, key = EXAMPLE_FACE) },
            )
        }
        if (channel) {
            CheckRow(
                title = stringResource(R.string.onboarding_glint_channel),
                checked = subscribe,
                onCheckedChange = onSubscribeChange,
                modifier = Modifier.padding(top = 12.dp),
                subtitle = stringResource(R.string.onboarding_glint_channel_hint),
            )
        }
    }
}

/** One chat of the glint's lesson, on the page itself rather than a card; TalkBack reads it as one line. */
@Composable
private fun Example(title: String, note: String, avatar: @Composable (String) -> Unit, mark: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Blesk.dimens.rowHeight)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        avatar(title)
        Column(Modifier.weight(1f)) {
            Text(title, style = Blesk.type.bodyStrong)
            Text(note, style = Blesk.type.label, color = Blesk.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        mark?.invoke()
    }
}

private val LockCircle = 72.dp

/** Both examples wear one colour, so the glint is the one difference the eye finds. */
private const val EXAMPLE_FACE = "glint"
