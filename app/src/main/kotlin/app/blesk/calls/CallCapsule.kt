package app.blesk.calls

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.R
import app.blesk.core.calls.CallManager
import app.blesk.core.calls.CallMedia
import app.blesk.core.calls.MediaState
import app.blesk.core.data.calls.CallPhase
import app.blesk.core.data.settings.GestureHint
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.fire.breathing
import app.blesk.core.design.glass.lens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.hintText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.blesk.feature.calls.R as CallsR

/**
 * A call that goes on while you are elsewhere in the app: a capsule under the top lens with the
 * other person (their edge breathes with their voice), the time, the microphone and a red end.
 * A tap brings the call screen back. Only «Завершить» ends the call.
 */
@Composable
internal fun CallCapsule(container: AppContainer) {
    val manager = container.calls.collectAsStateWithLifecycle().value ?: return
    val session by manager.session.collectAsStateWithLifecycle()
    val current = session?.takeIf { it.phase == CallPhase.Calling || it.phase == CallPhase.Connecting || it.phase == CallPhase.Active }
    val context = LocalContext.current
    val hint = hintText(GestureHint.CallCapsule)
    val seen by container.settings.hintsSeen.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(current != null, seen) {
        val known = seen ?: return@LaunchedEffect
        if (current != null && GestureHint.CallCapsule.name !in known) {
            container.notices.show(hint)
            container.settings.markHint(GestureHint.CallCapsule)
        }
    }
    AnimatedVisibility(
        visible = current != null,
        enter = fadeIn(Blesk.motion.press()) + expandVertically(Blesk.motion.settle()),
        exit = fadeOut(Blesk.motion.press()) + shrinkVertically(Blesk.motion.settle()),
    ) {
        val call = current ?: return@AnimatedVisibility
        val live by manager.media.state.collectAsStateWithLifecycle()
        val demo by container.demoCallMedia.collectAsStateWithLifecycle()
        val media = if (manager.demo) demo else live
        val scope = rememberCoroutineScope()
        val haptics = rememberHaptics()
        CallCapsuleLook(
            title = call.title,
            key = call.conversationId,
            time = callTime(call.startedAtMillis),
            micOn = media.micOn,
            level = media.members.filterNot { it.local }.maxOfOrNull { it.level } ?: 0f,
            onOpen = { context.startActivity(openCall(context)) },
            onMic = {
                haptics.toggle(!media.micOn)
                toggleMic(manager, container, media, scope)
            },
            onEnd = manager::hangUp,
        )
    }
}

/** The capsule itself: who, how long, the microphone and the red end. */
@Composable
internal fun CallCapsuleLook(
    title: String,
    key: String,
    time: String?,
    micOn: Boolean,
    level: Float,
    onOpen: () -> Unit,
    onMic: () -> Unit,
    onEnd: () -> Unit,
) {
    val shape = RoundedCornerShape(Blesk.dimens.capsuleRadius)
    val back = stringResource(R.string.call_capsule_open)
    Row(
        modifier = Modifier
            .padding(horizontal = Blesk.dimens.gutter)
            .padding(top = 6.dp)
            .fillMaxWidth()
            .lens(shape, elevation = 8.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClickLabel = back, onClick = onOpen)
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(title, size = 36.dp, key = key, modifier = Modifier.breathing(level))
        Column(Modifier.weight(1f)) {
            Text(title, style = Blesk.type.bodyStrong, maxLines = 1)
            Text(
                text = time ?: stringResource(R.string.call_capsule_connecting),
                style = Blesk.type.caption,
                color = Blesk.colors.textSecondary,
            )
        }
        IconButton(
            icon = if (micOn) BleskIcons.Mic else BleskIcons.MicOff,
            contentDescription = stringResource(if (micOn) CallsR.string.call_mic_off else CallsR.string.call_mic_on),
            onClick = onMic,
        )
        val end = stringResource(CallsR.string.call_hang_up)
        Icon(
            icon = BleskIcons.PhoneEnd,
            contentDescription = end,
            tint = Blesk.colors.onAccent,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Blesk.colors.danger)
                .clickable(role = Role.Button, onClickLabel = end, onClick = onEnd)
                .padding(8.dp),
        )
    }
}

/**
 * After a call that was talked through: «Звонок завершён · 04:12» and the detach vibration, on
 * whatever screen the call ended.
 */
@Composable
internal fun CallEndedNotice(container: AppContainer) {
    val manager = container.calls.collectAsStateWithLifecycle().value ?: return
    val haptics = rememberHaptics()
    val endedText = stringResource(R.string.call_ended_after)
    var startedAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(manager) {
        manager.session.collect { session ->
            val began = startedAt
            when {
                session?.startedAtMillis != null && session.phase == CallPhase.Active -> startedAt = session.startedAtMillis
                (session == null || session.phase == CallPhase.Ended) && began != null -> {
                    startedAt = null
                    container.notices.show(endedText.format(duration(System.currentTimeMillis() - began)))
                    haptics.detach()
                }
            }
        }
    }
}

@Composable
private fun callTime(startedAt: Long?): String? {
    startedAt ?: return null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(TICK_MILLIS)
        }
    }
    return duration(now - startedAt)
}

private fun duration(millis: Long): String {
    val seconds = (millis / MILLIS_PER_SECOND).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private fun toggleMic(manager: CallManager, container: AppContainer, media: MediaState, scope: CoroutineScope) {
    if (manager.demo) {
        container.demoCallMedia.value = media.copy(micOn = !media.micOn)
    } else {
        scope.launch { (manager.media as? CallMedia)?.setMic(!media.micOn) }
    }
}

private fun openCall(context: Context) =
    Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

private const val TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60
