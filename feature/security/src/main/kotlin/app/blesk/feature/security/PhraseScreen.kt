package app.blesk.feature.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlin.random.Random

class PhraseActions(
    val onCopy: () -> Unit,
    /** Null where no password manager is available. */
    val onPasswordManager: (() -> Unit)?,
    /** The three words were picked right. */
    val onChecked: () -> Unit,
    /** «Позже», offered only right after registration. */
    val onLater: (() -> Unit)?,
    val onBack: (() -> Unit)?,
)

private enum class Step { Show, Check, Done }

/**
 * The twelve words, each in a numbered capsule. Written down, they are checked by picking the
 * words under three random numbers; then the capsules run together into one key and catch fire.
 */
@Composable
fun PhraseScreen(words: List<String>, actions: PhraseActions, modifier: Modifier = Modifier, checked: Boolean = false) {
    var step by rememberSaveable { mutableStateOf(Step.Show) }
    ScrollScreen(title = stringResource(R.string.phrase_title), onBack = actions.onBack, modifier = modifier) {
        when (step) {
            Step.Show -> Show(words, actions, checked) { step = Step.Check }
            Step.Check -> Check(words, onBack = { step = Step.Show }) { step = Step.Done }
            Step.Done -> Done(words, actions.onChecked)
        }
    }
}

@Composable
private fun Show(words: List<String>, actions: PhraseActions, checked: Boolean, onCheck: () -> Unit) {
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    Text(stringResource(R.string.phrase_intro), color = Blesk.colors.textSecondary, modifier = pad.padding(bottom = 16.dp))
    Capsules(words)
    Column(pad.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            text = stringResource(if (checked) R.string.phrase_check_again else R.string.phrase_written),
            onClick = onCheck,
            modifier = Modifier.fillMaxWidth(),
        )
        actions.onPasswordManager?.let { save ->
            Button(
                text = stringResource(R.string.phrase_password_manager),
                kind = ButtonKind.Secondary,
                onClick = save,
                modifier = Modifier.fillMaxWidth(),
            )
            Hint(stringResource(R.string.phrase_password_manager_hint))
        }
        Wide(stringResource(R.string.phrase_copy), actions.onCopy)
        Hint(stringResource(R.string.phrase_copy_hint))
        actions.onLater?.let { later ->
            Wide(stringResource(R.string.phrase_later), later)
            Hint(stringResource(R.string.phrase_later_hint))
        }
    }
}

@Composable
private fun Wide(text: String, onClick: () -> Unit) {
    Button(text = text, kind = ButtonKind.Secondary, onClick = onClick, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Hint(text: String) {
    Text(text, style = Blesk.type.label, color = Blesk.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
}

/** Two columns of six, read down the first column and then the second, as on paper. */
@Composable
private fun Capsules(words: List<String>) {
    Row(Modifier.padding(horizontal = Blesk.dimens.gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        words.chunked(words.size / 2).forEachIndexed { column, half ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CapsuleGap)) {
                half.forEachIndexed { row, word -> WordCapsule(column * half.size + row + 1, word) }
            }
        }
    }
}

@Composable
private fun WordCapsule(number: Int, word: String, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.phrase_word, number, word)
    Row(
        modifier
            .fillMaxWidth()
            .height(CapsuleHeight)
            .clip(CircleShape)
            .background(Blesk.colors.surfaceStrong)
            .padding(horizontal = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", style = Blesk.type.label, color = Blesk.colors.textTertiary, modifier = Modifier.width(26.dp))
        Text(word, style = Blesk.type.bodyStrong)
    }
}

/** Three random numbers; for each, the right word among three others from the phrase's own list. */
@Composable
private fun Check(words: List<String>, onBack: () -> Unit, onPassed: () -> Unit) {
    val haptics = rememberHaptics()
    val asked = rememberSaveable { words.indices.shuffled().take(CHECKED_WORDS).sorted() }
    val options = remember(asked) {
        asked.associateWith { index ->
            val others = words.filterIndexed { i, _ -> i != index }.shuffled(Random(index)).take(OPTIONS - 1)
            (others + words[index]).shuffled(Random(index * SHUFFLE_SEED))
        }
    }
    val picked = remember { mutableStateMapOf<Int, String>() }
    var wrong by remember { mutableStateOf(false) }
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    Text(stringResource(R.string.phrase_check_intro), color = Blesk.colors.textSecondary, modifier = pad.padding(bottom = 8.dp))
    asked.forEach { index ->
        SectionHeader(stringResource(R.string.phrase_check_number, index + 1))
        Column(pad, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.getValue(index).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { option ->
                        Choice(option, picked[index] == option, Modifier.weight(1f)) {
                            picked[index] = option
                            wrong = false
                        }
                    }
                }
            }
        }
    }
    if (wrong) {
        Text(
            stringResource(R.string.phrase_check_wrong),
            color = Blesk.colors.danger,
            modifier = pad.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Column(pad.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            text = stringResource(R.string.phrase_check_done),
            onClick = {
                if (asked.all { picked[it] == words[it] }) {
                    haptics.success()
                    onPassed()
                } else {
                    haptics.refuse()
                    wrong = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Wide(stringResource(R.string.phrase_check_back), onBack)
    }
}

@Composable
private fun Choice(word: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(text = word, kind = if (selected) ButtonKind.Primary else ButtonKind.Secondary, onClick = onClick, modifier = modifier)
}

/**
 * The twelve capsules run together into one capsule, the key, and fire runs round its edge.
 * Without motion the key is simply there.
 */
@Composable
private fun Done(words: List<String>, onDone: () -> Unit) {
    val reduced = Blesk.motion.reduced
    val merge = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { merge.animateTo(1f, tween(MERGE_MILLIS)) }
    val color = Blesk.colors.surfaceStrong
    val density = LocalDensity.current
    val shape = RoundedCornerShape(CapsuleHeight / 2)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Blesk.dimens.gutter)) {
        val width = with(density) { maxWidth.toPx() }
        val capsule = with(density) { CapsuleHeight.toPx() }
        val gap = with(density) { CapsuleGap.toPx() }
        val column = with(density) { (maxWidth - 10.dp).toPx() / 2f }
        val fieldHeight = capsule * ROWS + gap * (ROWS - 1)
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { fieldHeight.toDp() })
                .metaballs(color, smoothness = 18.dp) {
                    val t = merge.value
                    val key = Rect(0f, fieldHeight / 2f - capsule / 2f, width, fieldHeight / 2f + capsule / 2f)
                    words.indices.map { index ->
                        val left = (index / ROWS) * (column + with(density) { 10.dp.toPx() })
                        val top = (index % ROWS) * (capsule + gap)
                        val start = Rect(left, top, left + column, top + capsule)
                        Blob(lerp(start, key, t), capsule / 2f)
                    }
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(CapsuleHeight)
                    .flashOn(key = merge.value >= 1f, shape = shape)
                    .graphicsLayer { alpha = ((merge.value - KEY_SHOWS_AT) / (1f - KEY_SHOWS_AT)).coerceIn(0f, 1f) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(BleskIcons.Lock, contentDescription = null, size = 18.dp)
                    Text(stringResource(R.string.phrase_key), style = Blesk.type.bodyStrong)
                }
            }
        }
    }
    Text(
        stringResource(R.string.phrase_done),
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(Blesk.dimens.gutter).semantics { liveRegion = LiveRegionMode.Polite },
    )
    Button(
        text = stringResource(R.string.phrase_finish),
        onClick = onDone,
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).fillMaxWidth(),
    )
}

private fun lerp(from: Rect, to: Rect, t: Float) = Rect(
    from.left + (to.left - from.left) * t,
    from.top + (to.top - from.top) * t,
    from.right + (to.right - from.right) * t,
    from.bottom + (to.bottom - from.bottom) * t,
)

private val CapsuleHeight: Dp = 48.dp
private val CapsuleGap: Dp = 8.dp
private const val ROWS = 6
private const val CHECKED_WORDS = 3
private const val OPTIONS = 4
private const val SHUFFLE_SEED = 31
private const val MERGE_MILLIS = 450
private const val KEY_SHOWS_AT = 0.7f
