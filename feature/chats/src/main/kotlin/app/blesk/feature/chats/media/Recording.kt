package app.blesk.feature.chats.media

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.core.data.model.Attachment
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.motion.rememberLoopPhase
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.CircleRecorder
import app.blesk.core.media.VoiceRecorder
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.sin

enum class RecordMode { Voice, Circle }

/** The recorders a conversation can use, and where finished recordings go. */
class Recorders(
    val voice: VoiceRecorder,
    val circle: CircleRecorder,
    val onVoice: (Attachment.Voice) -> Unit,
    val onCircle: (Attachment.Circle) -> Unit,
    /** The mode chosen last time; every chat opens with it. */
    val startMode: RecordMode = RecordMode.Voice,
    val onModeChange: (RecordMode) -> Unit = {},
    /** «Запись по нажатию»: a tap starts, the next tap on «Отправить» stops, a long press switches the mode. */
    val tapToRecord: Boolean = false,
)

/** Shared between the record button, the recording bar, the lock drop and the circle preview. */
@Stable
class RecordingUi {
    var mode by mutableStateOf(RecordMode.Voice)
    var active by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var dragX by mutableFloatStateOf(0f)
    var dragY by mutableFloatStateOf(0f)
    var startedAt by mutableStateOf(0L)

    fun reset() {
        active = false
        locked = false
        dragX = 0f
        dragY = 0f
    }
}

private val CancelDistance = 120.dp
private val LockDistance = 90.dp

/**
 * Microphone or camera. A tap switches between voice note and circle and says how to record;
 * holding records. Slide left to cancel, slide up to lock the recording and let go. Let go within
 * half a second and nothing is sent: that was most likely a tap that came out too long.
 */
@Composable
internal fun RecordButton(ui: RecordingUi, recorders: Recorders) {
    val context = LocalContext.current
    val notify = rememberNotify()
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val cancelPx = with(density) { CancelDistance.toPx() }
    val lockPx = with(density) { LockDistance.toPx() }
    val hint = stringResource(R.string.media_record_hint)
    val micDenied = stringResource(R.string.media_permission_mic)
    val cameraDenied = stringResource(R.string.media_permission_camera)
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { !it }) {
                haptics.warn()
                notify(if (ui.mode == RecordMode.Voice) micDenied else cameraDenied)
            }
        }
    fun needed(): Array<String> {
        val list = if (ui.mode == RecordMode.Voice) {
            listOf(Manifest.permission.RECORD_AUDIO)
        } else {
            listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        }
        return list.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
    }

    fun start(): Boolean {
        val missing = needed()
        if (missing.isNotEmpty()) {
            permissions.launch(missing)
            return false
        }
        val ok = when (ui.mode) {
            RecordMode.Voice -> recorders.voice.start(scope)
            RecordMode.Circle -> {
                scope.launch {
                    recorders.circle.open(owner)
                    recorders.circle.start()
                }
                true
            }
        }
        if (ok) {
            ui.active = true
            ui.startedAt = System.currentTimeMillis()
            haptics.tick()
        }
        return ok
    }

    fun switchMode() {
        ui.mode = if (ui.mode == RecordMode.Voice) RecordMode.Circle else RecordMode.Voice
        recorders.onModeChange(ui.mode)
        haptics.tick()
    }

    // Already on the lock: the recording bar's «Отправить» is the second tap.
    fun startLocked() {
        if (start()) ui.locked = true
    }

    val tapToRecord = recorders.tapToRecord
    val label =
        stringResource(if (ui.mode == RecordMode.Voice) R.string.media_record_voice else R.string.media_record_circle)
    val switchLabel = stringResource(R.string.media_record_switch)
    val recordLabel = stringResource(R.string.media_record_start)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics {
                contentDescription = label
                role = Role.Button
                // Holding a finger down is not something a screen reader can be asked for: recording is an action of its own.
                onClick(if (tapToRecord) recordLabel else switchLabel) {
                    if (tapToRecord) startLocked() else switchMode()
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction(if (tapToRecord) switchLabel else recordLabel) {
                        if (tapToRecord) switchMode() else startLocked()
                        true
                    },
                )
            }
            .pointerInput(ui, tapToRecord) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val holdMillis = if (tapToRecord) viewConfiguration.longPressTimeoutMillis else HOLD_MILLIS
                    val quick = withTimeoutOrNull(holdMillis) {
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                    }
                    if (tapToRecord) {
                        if (quick != null) {
                            startLocked()
                        } else {
                            switchMode()
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                        }
                        return@awaitEachGesture
                    }
                    if (quick != null) {
                        switchMode()
                        notify(hint)
                        return@awaitEachGesture
                    }
                    if (!start()) return@awaitEachGesture
                    var outcome: Gesture? = null
                    while (outcome == null) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change != null) {
                            ui.dragX = (change.position.x - down.position.x).coerceAtMost(0f)
                            ui.dragY = (change.position.y - down.position.y).coerceAtMost(0f)
                            change.consume()
                        }
                        outcome = when {
                            change == null || !change.pressed -> Gesture.Release
                            -ui.dragY > lockPx -> Gesture.Lock
                            -ui.dragX > cancelPx -> Gesture.Cancel
                            else -> null
                        }
                    }
                    when (outcome) {
                        Gesture.Release if SystemClock.uptimeMillis() - down.uptimeMillis < MIN_RECORD_MILLIS -> {
                            cancel(ui, recorders, scope)
                            haptics.refuse()
                            notify(hint)
                        }
                        Gesture.Release -> finish(ui, recorders, scope)
                        Gesture.Lock -> {
                            ui.locked = true
                            ui.dragY = 0f
                            haptics.stick()
                        }
                        Gesture.Cancel -> {
                            cancel(ui, recorders, scope)
                            haptics.detach()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (ui.mode == RecordMode.Voice) BleskIcons.Mic else BleskIcons.Camera, contentDescription = null)
    }
}

private const val HOLD_MILLIS = 220L
private const val MIN_RECORD_MILLIS = 500L

private enum class Gesture { Release, Lock, Cancel }

internal fun finish(ui: RecordingUi, recorders: Recorders, scope: kotlinx.coroutines.CoroutineScope) {
    val mode = ui.mode
    ui.reset()
    when (mode) {
        RecordMode.Voice -> recorders.voice.finish()?.let(recorders.onVoice)
        RecordMode.Circle -> scope.launch {
            recorders.circle.finish()?.let(recorders.onCircle)
            recorders.circle.close()
        }
    }
}

internal fun cancel(ui: RecordingUi, recorders: Recorders, scope: kotlinx.coroutines.CoroutineScope) {
    val mode = ui.mode
    ui.reset()
    when (mode) {
        RecordMode.Voice -> recorders.voice.cancel()
        RecordMode.Circle -> scope.launch { recorders.circle.close() }
    }
}

/** Replaces the text field while recording: a pulsing dot, the time, and how to cancel. */
@Composable
internal fun RecordingBar(ui: RecordingUi, recorders: Recorders, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val phase by rememberLoopPhase(1000)
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(ui.active) {
        while (ui.active) {
            now = System.currentTimeMillis()
            delay(TICK_MILLIS)
        }
    }
    val danger = Blesk.colors.danger
    Row(
        modifier = modifier.fillMaxWidth().height(48.dp).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.size(10.dp)) {
            drawCircle(danger.copy(alpha = 0.55f + 0.45f * sin(phase * 2f * PI).toFloat()))
        }
        Text(formatDuration(now - ui.startedAt), style = Blesk.type.bodyStrong)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (!ui.locked) {
                Text(
                    text = stringResource(R.string.media_slide_cancel),
                    style = Blesk.type.label,
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.graphicsLayer {
                        translationX = ui.dragX / 2f
                        alpha = 1f + ui.dragX / CANCEL_FADE
                    },
                )
            }
        }
        if (ui.locked) {
            IconButton(BleskIcons.Trash, stringResource(R.string.media_cancel), { cancel(ui, recorders, scope) })
            IconButton(BleskIcons.Send, stringResource(R.string.media_stop_send), { finish(ui, recorders, scope) })
        }
    }
}

private const val TICK_MILLIS = 200L
private const val CANCEL_FADE = 300f

/**
 * The lock above the record button: a drop that stretches up after the finger and comes away
 * once the recording is locked.
 */
@Composable
internal fun LockDrop(ui: RecordingUi, modifier: Modifier = Modifier) {
    val color = Blesk.colors.surfaceStrong
    val hint = stringResource(R.string.media_lock_hint)
    Box(
        modifier
            .size(width = 48.dp, height = 150.dp)
            .metaballs(color, smoothness = 22.dp) {
                val w = size.width
                val pull = (-ui.dragY / LockDistance.toPx()).coerceIn(0f, 1f)
                val top = size.height - w - (size.height - w) * (0.45f + 0.55f * pull)
                listOf(
                    Blob(Rect(0f, size.height - w, w, size.height), w / 2f),
                    Blob(Rect(w * 0.15f, top, w * 0.85f, top + w * 0.9f), w * 0.35f),
                )
            }
            .semantics { contentDescription = hint },
    ) {
        Icon(BleskIcons.Lock, contentDescription = null, size = 18.dp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
    }
}

/** The camera inside the circle while a round video is being recorded, with a ring for the time left. */
@Composable
internal fun CircleOverlay(ui: RecordingUi, recorders: Recorders) {
    val request by recorders.circle.preview.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(ui.active) {
        while (ui.active) {
            now = System.currentTimeMillis()
            delay(TICK_MILLIS)
        }
    }
    val progress = ((now - ui.startedAt).toFloat() / CircleRecorder.MAX_DURATION_MILLIS).coerceIn(0f, 1f)
    val ring = Blesk.colors.light
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(280.dp).clip(CircleShape).background(Blesk.colors.surface)) {
            request?.let {
                CameraXViewfinder(surfaceRequest = it, implementationMode = ImplementationMode.EMBEDDED, modifier = Modifier.fillMaxSize())
            }
        }
        Canvas(Modifier.size(296.dp)) {
            val stroke = 4.dp.toPx()
            drawArc(
                color = ring,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}
