package app.blesk.feature.chats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Conversation
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.StateMessage
import app.blesk.core.design.component.Text
import app.blesk.core.design.glass.lens
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.LocalClock
import app.blesk.core.motion.rememberHaptics
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** What can be done with a message request. */
class RequestActions(
    val onAccept: (Conversation) -> Unit,
    val onDelete: (Conversation) -> Unit,
    val onBlock: (Conversation) -> Unit,
    val onOpen: (Conversation) -> Unit,
)

/**
 * The row of «Запросы» above the chats: the people who wrote first, fanned out like a small
 * stack of cards, and how many there are.
 */
@Composable
internal fun RequestsRow(requests: ImmutableList<Conversation>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(Blesk.dimens.avatar)) {
            requests.take(STACK).reversed().forEachIndexed { index, request ->
                val shift = (STACK - 1 - index).coerceAtMost(requests.size - 1)
                Avatar(
                    name = request.title,
                    key = request.id,
                    size = Blesk.dimens.avatar - 8.dp,
                    modifier = Modifier.offset(x = (shift * 4).dp, y = (shift * 4).dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.requests_title), style = Blesk.type.bodyStrong)
            Text(
                text = pluralStringResource(R.plurals.requests_count, requests.size, requests.size),
                style = Blesk.type.label,
                color = Blesk.colors.textSecondary,
            )
        }
    }
}

/**
 * Message requests as a stack of cards: swipe right to accept, left to delete, up to block. The
 * same is on buttons, for TalkBack and for those who prefer them. «Все запросы» lays them out
 * as a plain list.
 */
@Composable
fun RequestsScreen(requests: ImmutableList<Conversation>, actions: RequestActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var asList by rememberSaveable { mutableStateOf(false) }
    ScrollScreen(title = stringResource(R.string.requests_title), onBack = onBack, modifier = modifier) {
        if (requests.isEmpty()) {
            StateMessage(stringResource(R.string.requests_empty))
            return@ScrollScreen
        }
        Text(
            text = stringResource(R.string.requests_hint),
            color = Blesk.colors.textSecondary,
            style = Blesk.type.label,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).padding(bottom = 12.dp),
        )
        if (asList) {
            Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
                requests.forEach { request ->
                    ConversationSummary(
                        conversation = request,
                        typing = false,
                        now = LocalClock.current(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { actions.onOpen(request) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        } else {
            CardStack(requests, actions)
        }
        Button(
            text = stringResource(if (asList) R.string.requests_as_stack else R.string.requests_all),
            kind = ButtonKind.Secondary,
            onClick = { asList = !asList },
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 16.dp),
        )
    }
}

@Composable
private fun CardStack(requests: ImmutableList<Conversation>, actions: RequestActions) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CardHeight + StackDepth * (STACK - 1))
            .padding(horizontal = Blesk.dimens.gutter),
        contentAlignment = Alignment.TopCenter,
    ) {
        requests.take(STACK).reversed().forEachIndexed { index, request ->
            val depth = minOf(STACK, requests.size) - 1 - index
            key(request.id) { RequestCard(request, depth, actions) }
        }
    }
}

@Composable
private fun RequestCard(request: Conversation, depth: Int, actions: RequestActions) {
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val reach = with(LocalDensity.current) { SwipeReach.toPx() }
    val top = depth == 0
    val accept = stringResource(R.string.requests_accept)
    val delete = stringResource(R.string.requests_delete)
    val block = stringResource(R.string.person_block)
    Column(
        modifier = Modifier
            .offset { IntOffset(drag.value.x.roundToInt(), drag.value.y.roundToInt() + (depth * StackDepth.toPx()).roundToInt()) }
            .graphicsLayer {
                val scale = 1f - depth * DEPTH_SHRINK
                scaleX = scale
                scaleY = scale
                rotationZ = drag.value.x / reach * TILT
            }
            .fillMaxWidth()
            .height(CardHeight)
            .lens(RoundedCornerShape(Blesk.dimens.cardRadius), elevation = 10.dp)
            .then(
                if (!top) {
                    Modifier
                } else {
                    Modifier
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(accept) { actions.onAccept(request); true },
                                CustomAccessibilityAction(delete) { actions.onDelete(request); true },
                                CustomAccessibilityAction(block) { actions.onBlock(request); true },
                            )
                        }
                        .pointerInput(request.id) {
                            detectDragGestures(
                                onDragEnd = {
                                    val (x, y) = drag.value
                                    val decided = when {
                                        y < -reach && abs(y) > abs(x) -> actions.onBlock
                                        x > reach -> actions.onAccept
                                        x < -reach -> actions.onDelete
                                        else -> null
                                    }
                                    if (decided != null) {
                                        haptics.detach()
                                        decided(request)
                                    } else {
                                        scope.launch { drag.animateTo(Offset.Zero) }
                                    }
                                },
                                onDragCancel = { scope.launch { drag.animateTo(Offset.Zero) } },
                            ) { change, amount ->
                                change.consume()
                                scope.launch { drag.snapTo(drag.value + amount) }
                            }
                        }
                },
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // A stranger shows only a monogram and their username until accepted.
        Avatar(request.title, key = request.id, size = 64.dp)
        Text(request.title, style = Blesk.type.headline, maxLines = 1)
        Text(
            text = request.preview?.text.orEmpty(),
            color = Blesk.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 3,
            modifier = Modifier.weight(1f),
        )
        if (top) {
            Button(accept, onClick = { actions.onAccept(request) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(delete, onClick = { actions.onDelete(request) }, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
                Button(block, onClick = { actions.onBlock(request) }, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
            }
        }
    }
}

private const val STACK = 3
private const val DEPTH_SHRINK = 0.05f
private const val TILT = 8f
private val CardHeight = 340.dp
private val StackDepth = 12.dp
private val SwipeReach = 110.dp
