package app.blesk.feature.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blesk.core.calls.CallMember
import app.blesk.core.calls.MediaState
import app.blesk.core.data.calls.CallOutcome
import app.blesk.core.data.calls.CallPhase
import app.blesk.core.data.calls.CallSession
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.ChatBackdrop
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.glint
import app.blesk.core.design.fire.beacon
import app.blesk.core.design.fire.breathing
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.glass.BottomLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import io.livekit.android.room.Room
import kotlinx.coroutines.delay

/** Everything the call screen can ask for. */
class CallActions(
    val accept: () -> Unit,
    val decline: () -> Unit,
    val hangUp: () -> Unit,
    val toggleMic: () -> Unit,
    val toggleCamera: () -> Unit,
    val flipCamera: () -> Unit,
    val toggleSpeaker: () -> Unit,
    /** Null where sharing the screen is not possible (the demo, voice-only). */
    val toggleShare: (() -> Unit)?,
)

@Composable
fun CallScreen(
    session: CallSession,
    media: MediaState,
    room: Room?,
    nameOf: (String) -> String,
    actions: CallActions,
    modifier: Modifier = Modifier,
    demo: Boolean = false,
    /** In the demo, the phone's own camera, shown in the corner as in a real video call. */
    demoPreview: @Composable BoxScope.() -> Unit = {},
    clock: () -> Long = System::currentTimeMillis,
) {
    val haptics = rememberHaptics()
    LaunchedEffect(session.phase) {
        when (session.phase) {
            CallPhase.Active -> haptics.success()
            CallPhase.Ended -> haptics.callEnded()
            else -> Unit
        }
    }
    val remote = media.members.filterNot { it.local }
    val local = media.members.firstOrNull { it.local }
    val sharedScreen = remote.firstNotNullOfOrNull { it.screen }
    val stage = sharedScreen ?: if (!session.voiceChannel) remote.firstNotNullOfOrNull { it.camera } else null
    Box(modifier.fillMaxSize().background(Blesk.colors.background)) {
        // The light of the person's own header style behind them, never a blank screen.
        if (stage == null) {
            val preset = Math.floorMod(session.conversationId.hashCode(), HeaderStyle.Light.PRESETS)
            ChatBackdrop(HeaderStyle.Light(preset), photo = null, modifier = Modifier.fillMaxSize())
        }
        when {
            room != null && stage != null -> VideoView(room, stage, Modifier.fillMaxSize(), fill = stage != sharedScreen)
            session.voiceChannel -> VoiceGrid(media.members, nameOf, Modifier.align(Alignment.Center))
            else -> Who(session, remote.firstOrNull()?.level ?: 0f, Modifier.align(Alignment.Center)) {
                Status(session, media, demo, clock)
                Encrypted()
            }
        }
        // Over a picture or a grid the name and the time go to the top; face to face they sit under the avatar.
        if (stage != null || session.voiceChannel) {
            Column(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(session.title, style = Blesk.type.headline)
                Status(session, media, demo, clock)
                Encrypted()
            }
        }
        val ownCamera = local?.camera
        // The demo shows your own camera whenever it is on, also once turned on during a voice call.
        val demoCamera = demo && media.cameraOn
        if (demoCamera && session.phase != CallPhase.Ended) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp)
                    .size(width = 108.dp, height = 156.dp)
                    .clip(RoundedCornerShape(20.dp)),
                content = demoPreview,
            )
        } else if (room != null && ownCamera != null && stage != null) {
            VideoView(
                room = room,
                track = ownCamera,
                mirror = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp)
                    .size(width = 108.dp, height = 156.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
        }
        Controls(session, media, actions, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Who(session: CallSession, level: Float, modifier: Modifier, details: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The beam goes round while it rings; once the call connects, fire runs round the edge.
        Avatar(
            session.title,
            size = 120.dp,
            modifier = Modifier
                .beacon(active = session.phase in Ringing)
                .flashOn(key = session.phase == CallPhase.Active, shape = CircleShape)
                .breathing(level),
        )
        Spacer(Modifier.height(20.dp))
        Text(session.title, style = Blesk.type.headline, textAlign = TextAlign.Center)
        details()
    }
}

@Composable
private fun Status(session: CallSession, media: MediaState, demo: Boolean, clock: () -> Long) {
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(session.phase) {
        while (session.phase == CallPhase.Active) {
            now = clock()
            delay(TICK_MILLIS)
        }
    }
    val text = when (session.phase) {
        CallPhase.Ringing -> stringResource(if (session.video) R.string.call_incoming_video else R.string.call_incoming)
        CallPhase.Calling -> stringResource(R.string.call_calling)
        CallPhase.Connecting -> stringResource(R.string.call_connecting)
        CallPhase.Active -> when {
            media.reconnecting -> stringResource(R.string.call_reconnecting)
            // The demo has nobody to wait for: it counts the call like any other.
            !demo && session.voiceChannel && !media.othersJoined -> stringResource(R.string.call_alone)
            !demo && !session.voiceChannel && !media.othersJoined -> stringResource(R.string.call_waiting)
            else -> duration(now - (session.startedAtMillis ?: now))
        }
        CallPhase.Ended -> stringResource(
            when (session.outcome) {
                CallOutcome.Declined -> R.string.call_declined
                CallOutcome.Busy -> R.string.call_busy
                CallOutcome.Missed -> R.string.call_missed
                CallOutcome.NoAnswer -> R.string.call_no_answer
                CallOutcome.Failed -> R.string.call_failed
                CallOutcome.Unavailable -> R.string.call_unavailable
                CallOutcome.HangUp, null -> R.string.call_ended
            },
        )
    }
    Text(
        text = text,
        style = Blesk.type.label,
        color = Blesk.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The glint is the mark of end-to-end encryption, here as everywhere. */
@Composable
private fun Encrypted() {
    val shape = CircleShape
    Row(
        Modifier
            .padding(top = 4.dp)
            .clip(shape)
            .background(Blesk.colors.surface)
            .glint(shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(BleskIcons.Lock, contentDescription = null, size = 14.dp, tint = Blesk.colors.textSecondary)
        Text(stringResource(R.string.call_encrypted), style = Blesk.type.caption, color = Blesk.colors.textSecondary)
    }
}

@Composable
private fun VoiceGrid(members: List<CallMember>, nameOf: (String) -> String, modifier: Modifier) {
    val you = stringResource(R.string.call_you)
    FlowRow(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        members.forEach { member ->
            val name = if (member.local) you else nameOf(member.id)
            val label = when {
                !member.micOn -> stringResource(R.string.call_muted, name)
                member.speaking -> stringResource(R.string.call_speaking, name)
                else -> name
            }
            Column(
                Modifier.width(88.dp).semantics(mergeDescendants = true) { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar(
                        name = name,
                        size = 72.dp,
                        modifier = Modifier
                            .breathing(member.level)
                            .border(2.dp, if (member.speaking) Blesk.colors.light else Color.Transparent, CircleShape),
                    )
                    if (!member.micOn) {
                        Box(Modifier.clip(CircleShape).background(Blesk.colors.surfaceStrong).padding(4.dp)) {
                            Icon(BleskIcons.MicOff, contentDescription = null, size = 16.dp)
                        }
                    }
                }
                Text(name, style = Blesk.type.label, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun Controls(session: CallSession, media: MediaState, actions: CallActions, modifier: Modifier) {
    BottomLens(modifier) {
        if (session.phase == CallPhase.Ringing) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RoundButton(BleskIcons.PhoneEnd, stringResource(R.string.call_decline), actions.decline, Blesk.colors.danger, size = 72.dp)
                RoundButton(
                    icon = if (session.video) BleskIcons.Video else BleskIcons.Phone,
                    label = stringResource(R.string.call_accept),
                    onClick = actions.accept,
                    color = Blesk.colors.accent,
                    tint = Blesk.colors.onAccent,
                    size = 72.dp,
                )
            }
            return@BottomLens
        }
        val live = session.phase != CallPhase.Ended
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live) {
                RoundButton(
                    if (media.micOn) BleskIcons.Mic else BleskIcons.MicOff,
                    stringResource(if (media.micOn) R.string.call_mic_off else R.string.call_mic_on),
                    actions.toggleMic,
                    active = !media.micOn,
                )
                if (!session.voiceChannel) {
                    RoundButton(
                        if (media.cameraOn) BleskIcons.Video else BleskIcons.VideoOff,
                        stringResource(if (media.cameraOn) R.string.call_camera_off else R.string.call_camera_on),
                        actions.toggleCamera,
                        active = media.cameraOn,
                    )
                }
                if (media.cameraOn) RoundButton(BleskIcons.Flip, stringResource(R.string.call_flip), actions.flipCamera)
                RoundButton(
                    BleskIcons.Speaker,
                    stringResource(if (media.speakerOn) R.string.call_speaker_off else R.string.call_speaker_on),
                    actions.toggleSpeaker,
                    active = media.speakerOn,
                )
                actions.toggleShare?.let { share ->
                    RoundButton(
                        BleskIcons.Screen,
                        stringResource(if (media.sharing) R.string.call_share_off else R.string.call_share_on),
                        share,
                        active = media.sharing,
                    )
                }
            }
            if (live) {
                RoundButton(BleskIcons.PhoneEnd, stringResource(R.string.call_hang_up), actions.hangUp, Blesk.colors.danger, size = 60.dp)
            }
        }
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    size: Dp = 52.dp,
    active: Boolean = false,
) {
    val background = when {
        color != Color.Unspecified -> color
        active -> Blesk.colors.textPrimary
        else -> Blesk.colors.surfaceStrong
    }
    val iconTint = when {
        tint != Color.Unspecified -> tint
        color != Color.Unspecified -> Blesk.colors.background
        active -> Blesk.colors.background
        else -> Blesk.colors.textPrimary
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, size = 24.dp)
    }
}

private fun duration(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, seconds / 60 % 60, seconds % 60)
    } else {
        "%d:%02d".format(seconds / 60, seconds % 60)
    }
}

private const val TICK_MILLIS = 1_000L

/** The phases before anyone talks, while the beam goes round. */
private val Ringing = setOf(CallPhase.Calling, CallPhase.Ringing, CallPhase.Connecting)
