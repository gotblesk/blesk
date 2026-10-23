package app.blesk.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.SoonTag
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.fire.passingLight
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sign
import app.blesk.core.design.R as DesignR

/**
 * «Создать аккаунт», «У меня уже есть аккаунт» and «Попробовать демо», stuck together into one
 * drop. A choice comes away from the drop before it is carried out: the others let go of it and
 * the bridge breaks. Creating starts at once and comes back together if it fails. With
 * animations off the capsules stand apart and a choice is carried out at once.
 */
@Composable
internal fun WelcomeCluster(busy: Boolean, problem: AccessProblem?, actions: WelcomeActions, modifier: Modifier = Modifier) {
    val motion = Blesk.motion
    val haptics = rememberHaptics()
    val notify = rememberNotify()
    val scope = rememberCoroutineScope()
    val apart = remember { Animatable(0f) }
    var chosen by remember { mutableIntStateOf(NONE) }
    // Where each capsule rests, as state, so the drop is drawn again when they move.
    val resting = remember { mutableStateListOf<Rect?>(null, null, null) }
    val soon = stringResource(R.string.onboarding_have_account_soon)

    // A failed attempt brings the drop back together without a sound: the refusal has already been felt.
    LaunchedEffect(busy, problem) {
        if (busy || problem == null || chosen != CREATE) return@LaunchedEffect
        apart.animateTo(0f, motion.settle())
        chosen = NONE
    }

    fun choose(index: Int, start: () -> Unit = {}, then: () -> Unit = {}) {
        if (busy || chosen != NONE) return
        start()
        if (motion.reduced) {
            then()
            return
        }
        chosen = index
        scope.launch {
            apart.animateTo(1f, motion.signature())
            haptics.detach()
            then()
        }
    }

    fun direction(index: Int): Float = if (chosen == NONE) 0f else (index - chosen).toFloat().sign

    Column(
        modifier
            .widthIn(max = MaxWidth)
            .fillMaxWidth()
            .then(
                if (motion.reduced) {
                    Modifier
                } else {
                    Modifier.metaballs(Blesk.colors.surfaceStrong, smoothness = Smoothness) {
                        val shift = Detach.toPx() * apart.value
                        val inset = PrimaryInset.toPx()
                        resting.mapIndexedNotNull { index, rect ->
                            // The lit capsule draws its own fill; its drop stays just inside it, so no rim shows round it.
                            val drop = rect?.translate(0f, direction(index) * shift)?.let { if (index == CREATE) it.deflate(inset) else it }
                            drop?.let { Blob(it, it.height / 2f) }
                        }
                    }
                },
            ),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        val ways = listOf(
            stringResource(R.string.onboarding_create),
            stringResource(R.string.onboarding_have_account),
            stringResource(R.string.onboarding_demo),
        )
        ways.forEachIndexed { index, text ->
            val ready = index != HAVE_ACCOUNT || actions.onHaveAccount != null
            WayCapsule(
                text = text,
                primary = index == CREATE,
                ready = ready,
                busy = busy && index == CREATE,
                onClick = {
                    when {
                        !ready -> notify(soon)
                        index == CREATE -> choose(index, start = actions.onCreate)
                        index == HAVE_ACCOUNT -> choose(index, then = { actions.onHaveAccount?.invoke() })
                        else -> choose(index, then = actions.onDemo)
                    }
                },
                modifier = Modifier
                    .onPlaced { resting[index] = it.boundsInParent() }
                    .offset { IntOffset(0, (direction(index) * apart.value * Detach.toPx()).roundToInt()) },
            )
        }
    }
}

/**
 * One way in. The lit one creates an account; one that is not [ready] wears «скоро», and a tap
 * only says when it comes. Without animations each capsule has its own fill instead of the drop.
 */
@Composable
private fun WayCapsule(text: String, primary: Boolean, ready: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Blesk.colors
    val fill = when {
        primary -> Modifier.background(colors.accent)
        Blesk.motion.reduced -> Modifier.background(colors.surfaceStrong)
        else -> Modifier
    }
    val soonLabel = stringResource(DesignR.string.design_soon)
    val wait = stringResource(DesignR.string.design_wait)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CapsuleHeight)
            .clip(CircleShape)
            .then(fill)
            .passingLight(busy, CircleShape, fill = colors.accent, content = colors.onAccent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                if (!ready) contentDescription = "$text, $soonLabel"
                if (busy) stateDescription = wait
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Text(
            text = text,
            style = Blesk.type.bodyStrong,
            color = when {
                primary -> colors.onAccent
                ready -> colors.textPrimary
                else -> colors.textSecondary
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!ready) SoonTag()
    }
}

private const val NONE = -1
private const val CREATE = 0
private const val HAVE_ACCOUNT = 1

private val CapsuleHeight = 52.dp
private val Gap = 8.dp
private val Smoothness = 18.dp

/** How far the others let go of a chosen capsule: well past [Smoothness], so the bridge breaks. */
private val Detach = 24.dp
private val PrimaryInset = 1.dp
private val MaxWidth = 360.dp
