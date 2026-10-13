package app.blesk.feature.chats.media

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.core.data.model.Attachment
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Slider
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.blockTaps
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.motion.rememberLoopPhase
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.CircleRecorder
import app.blesk.core.media.MediaTrimmer
import app.blesk.core.media.Piece
import app.blesk.core.media.VoiceRecorder
import app.blesk.core.media.Waveform
import app.blesk.core.motion.Viscous
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

enum class RecordMode { Voice, Circle }

/** The recorders a conversation can use, and where finished recordings go. */
class Recorders(
    val conversationId: String,
    val voice: VoiceRecorder,
    val circle: CircleRecorder,
    val onVoice: (Attachment.Voice) -> Unit,
    val onCircle: (Attachment.Circle) -> Unit,
    /** The mode chosen last time; every chat opens with it. */
    val startMode: RecordMode = RecordMode.Voice,
    val onModeChange: (RecordMode) -> Unit = {},
    /** «Запись по нажатию»: a tap starts, the next tap on «Отправить» stops, a long press switches the mode. */
    val tapToRecord: Boolean = false,
    /** A voice note got locked: from here it may go on in the background. */
    val onLock: () -> Unit = {},
    /** Joins and trims recordings before they are sent; without it a recording goes as it was made. */
    val trimmer: MediaTrimmer? = null,
)

/** A recording stopped with «Стоп», waiting to be listened to, trimmed and sent. */
sealed interface RecordDraft {
    /** A voice note; recorded on with «Дописать», it is several pieces played one after another. */
    data class Voice(val pieces: List<Attachment.Voice>) : RecordDraft {
        val durationMillis: Long get() = pieces.sumOf { it.durationMillis }
    }

    data class Circle(val circle: Attachment.Circle) : RecordDraft
}

/** Shared between the record button, the recording bar, the lock drop and the circle preview. */
@Stable
class RecordingUi {
    var mode by mutableStateOf(RecordMode.Voice)
    var active by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var dragX by mutableFloatStateOf(0f)
    var dragY by mutableFloatStateOf(0f)
    var startedAt by mutableStateOf(0L)
    var paused by mutableStateOf(false)

    /** The recording that was refused its permission; the screen shows how to grant it in the settings. */
    var denied by mutableStateOf<RecordMode?>(null)

    /** What «Стоп» left to listen to before sending. */
    var draft by mutableStateOf<RecordDraft?>(null)

    /** A trimmed or joined recording is being put together before it goes. */
    var exporting by mutableStateOf(false)
    private var pausedAt = 0L
    private var pausedTotal = 0L

    /** How long has been recorded by [now], pauses left out. */
    fun elapsed(now: Long): Long = (if (paused) pausedAt else now) - startedAt - pausedTotal

    internal fun markPaused(on: Boolean) {
        val now = System.currentTimeMillis()
        if (on) pausedAt = now else pausedTotal += now - pausedAt
        paused = on
    }

    fun reset() {
        active = false
        locked = false
        paused = false
        pausedTotal = 0L
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
    val busy = stringResource(R.string.media_record_busy)
    val micWhy = stringResource(R.string.media_permission_mic)
    val cameraWhy = stringResource(R.string.media_permission_camera)
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { !it }) {
                haptics.warn()
                ui.denied = ui.mode
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
            // The system dialog cannot say why; the line under it does.
            notify(if (ui.mode == RecordMode.Voice) micWhy else cameraWhy)
            permissions.launch(missing)
            return false
        }
        if (recorders.voice.active.value && recorders.voice.owner != recorders.conversationId) {
            haptics.refuse()
            notify(busy)
            return false
        }
        val ok = begin(ui, recorders, owner, scope)
        if (ok) haptics.tick()
        return ok
    }

    fun switchMode() {
        ui.mode = if (ui.mode == RecordMode.Voice) RecordMode.Circle else RecordMode.Voice
        recorders.onModeChange(ui.mode)
        haptics.tick()
    }

    fun lock() {
        ui.locked = true
        ui.dragY = 0f
        if (ui.mode == RecordMode.Voice) recorders.onLock()
    }

    // Already on the lock: the recording bar's «Отправить» is the second tap.
    fun startLocked() {
        if (start()) lock()
    }

    // Held, the button flows out within a tenth of a second into a capsule twice its size under the
    // finger, filled, with a halo of light around it that breathes with the voice.
    val holding = ui.active && !ui.locked
    val grow by animateFloatAsState(if (holding) HOLD_SCALE else 1f, tween(GROW_MILLIS, easing = Viscous), label = "recordGrow")
    val loudness by recorders.voice.loudness.collectAsStateWithLifecycle()
    val breath by animateFloatAsState(if (holding) loudness else 0f, tween(BREATH_MILLIS), label = "recordBreath")
    val fill = Blesk.colors.accent
    val halo = Blesk.colors.light
    val tapToRecord = recorders.tapToRecord
    val label =
        stringResource(if (ui.mode == RecordMode.Voice) R.string.media_record_voice else R.string.media_record_circle)
    val switchLabel = stringResource(R.string.media_record_switch)
    val recordLabel = stringResource(R.string.media_record_start)
    Box(
        modifier = Modifier
            .size(48.dp)
            .zIndex(1f)
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .drawBehind {
                val shown = ((grow - 1f) / (HOLD_SCALE - 1f)).coerceIn(0f, 1f)
                if (shown <= 0f) return@drawBehind
                val radius = size.minDimension / 2f * (HALO_REST + HALO_SWING * breath)
                val glow = Brush.radialGradient(listOf(halo.copy(alpha = HALO_ALPHA * shown), halo.copy(alpha = 0f)), radius = radius)
                drawCircle(glow, radius)
                drawCircle(fill.copy(alpha = shown))
            }
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
                        // The finger was lifted by the app going away (a call, the home button), not by the person.
                        Gesture.Release if !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) -> {
                            ui.locked = true
                            ui.dragY = 0f
                            pause(ui, recorders)
                        }
                        Gesture.Release if SystemClock.uptimeMillis() - down.uptimeMillis < MIN_RECORD_MILLIS -> {
                            cancel(ui, recorders, scope)
                            haptics.refuse()
                            notify(hint)
                        }
                        Gesture.Release -> finish(ui, recorders, scope)
                        Gesture.Lock -> {
                            lock()
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
        Icon(
            if (ui.mode == RecordMode.Voice) BleskIcons.Mic else BleskIcons.Camera,
            contentDescription = null,
            tint = if (holding) Blesk.colors.onAccent else Blesk.colors.textPrimary,
        )
    }
}

private const val HOLD_SCALE = 2f
private const val GROW_MILLIS = 100
private const val BREATH_MILLIS = 90
private const val HALO_REST = 1.25f
private const val HALO_SWING = 0.45f
private const val HALO_ALPHA = 0.55f

/** Starts recording in the current mode, permissions already granted. */
internal fun begin(ui: RecordingUi, recorders: Recorders, owner: LifecycleOwner, scope: CoroutineScope): Boolean {
    val ok = when (ui.mode) {
        RecordMode.Voice -> recorders.voice.start(recorders.conversationId)
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
    }
    return ok
}

private const val HOLD_MILLIS = 220L
private const val MIN_RECORD_MILLIS = 500L

private enum class Gesture { Release, Lock, Cancel }

/** «Отправить» while recording, or letting go without the lock: it goes at once, joined with what «Дописать» added to. */
internal fun finish(ui: RecordingUi, recorders: Recorders, scope: CoroutineScope) {
    val mode = ui.mode
    val before = (ui.draft as? RecordDraft.Voice)?.pieces.orEmpty()
    ui.reset()
    ui.draft = null
    when (mode) {
        RecordMode.Voice -> sendVoice(before + listOfNotNull(recorders.voice.finish()), 0f..1f, ui, recorders, scope)
        RecordMode.Circle -> scope.launch {
            recorders.circle.finish()?.let(recorders.onCircle)
            recorders.circle.close()
        }
    }
}

/** «Стоп»: the recording waits to be listened to, trimmed, recorded on and sent. */
internal fun stop(ui: RecordingUi, recorders: Recorders, scope: CoroutineScope) {
    val mode = ui.mode
    val before = (ui.draft as? RecordDraft.Voice)?.pieces.orEmpty()
    ui.reset()
    when (mode) {
        RecordMode.Voice -> {
            val pieces = before + listOfNotNull(recorders.voice.finish())
            ui.draft = if (pieces.isEmpty()) null else RecordDraft.Voice(pieces)
        }
        RecordMode.Circle -> scope.launch {
            val circle = recorders.circle.finish()
            recorders.circle.close()
            ui.draft = circle?.let { RecordDraft.Circle(it) }
        }
    }
}

/**
 * [pieces] as one voice note, kept within [range] (shares of the whole). A single untouched
 * piece goes as it is; anything joined or trimmed is put together first.
 */
internal fun sendVoice(
    pieces: List<Attachment.Voice>,
    range: ClosedFloatingPointRange<Float>,
    ui: RecordingUi,
    recorders: Recorders,
    scope: CoroutineScope,
) {
    if (pieces.isEmpty()) return
    val whole = range.start <= 0f && range.endInclusive >= 1f
    val trimmer = recorders.trimmer
    if (pieces.size == 1 && whole) return recorders.onVoice(pieces.single())
    if (trimmer == null) return pieces.forEach(recorders.onVoice)
    val total = pieces.sumOf { it.durationMillis }
    val from = (range.start * total).toLong()
    val to = (range.endInclusive * total).toLong()
    ui.exporting = true
    scope.launch {
        val file = trimmer.joinAudio(pieces.map { Piece(it.path, it.durationMillis) }, from, to)
        ui.exporting = false
        if (file != null) {
            val bars = sliceBars(draftBars(pieces), range)
            recorders.onVoice(Attachment.Voice(file.path, to - from, Waveform.encode(bars.toList())))
            pieces.forEach { File(it.path).delete() }
        }
    }
}

/** The round video kept within [range]; untouched, it goes as it was recorded. */
internal fun sendCircle(
    circle: Attachment.Circle,
    range: ClosedFloatingPointRange<Float>,
    ui: RecordingUi,
    recorders: Recorders,
    scope: CoroutineScope,
) {
    val trimmer = recorders.trimmer
    if ((range.start <= 0f && range.endInclusive >= 1f) || trimmer == null) return recorders.onCircle(circle)
    val from = (range.start * circle.durationMillis).toLong()
    val to = (range.endInclusive * circle.durationMillis).toLong()
    ui.exporting = true
    scope.launch {
        val file = trimmer.clipVideo(circle.path, from, to)
        ui.exporting = false
        if (file != null) {
            recorders.onCircle(circle.copy(path = file.path, durationMillis = to - from))
            File(circle.path).delete()
        }
    }
}

/** «Удалить» in the preview: the recording and its files are gone. */
internal fun discard(ui: RecordingUi) {
    when (val draft = ui.draft) {
        is RecordDraft.Voice -> draft.pieces.forEach { File(it.path).delete() }
        is RecordDraft.Circle -> {
            File(draft.circle.path).delete()
            File(draft.circle.poster).delete()
        }
        null -> Unit
    }
    ui.draft = null
}

/** The loudness of [pieces] played one after another, each taking room by how long it is. */
internal fun draftBars(pieces: List<Attachment.Voice>): FloatArray {
    val total = pieces.sumOf { it.durationMillis }.coerceAtLeast(1)
    val bars = ArrayList<Float>()
    pieces.forEach { piece ->
        val own = Waveform.decode(piece.waveform)
        val count = (DRAFT_BARS * piece.durationMillis / total).toInt().coerceAtLeast(1)
        repeat(count) { i -> bars += own.getOrElse(i * own.size / count) { 0f } }
    }
    return bars.toFloatArray()
}

/** The bars within [range], spread back to the usual number. */
internal fun sliceBars(bars: FloatArray, range: ClosedFloatingPointRange<Float>): FloatArray {
    if (bars.isEmpty()) return bars
    val from = (range.start * bars.size).toInt().coerceIn(0, bars.lastIndex)
    val to = (range.endInclusive * bars.size).toInt().coerceIn(from + 1, bars.size)
    val kept = bars.copyOfRange(from, to)
    return FloatArray(DRAFT_BARS) { i -> kept[i * kept.size / DRAFT_BARS] }
}

private const val DRAFT_BARS = 64

internal fun pause(ui: RecordingUi, recorders: Recorders) {
    if (!ui.active || ui.paused) return
    when (ui.mode) {
        RecordMode.Voice -> recorders.voice.pause()
        RecordMode.Circle -> recorders.circle.pause()
    }
    ui.markPaused(true)
}

internal fun resume(ui: RecordingUi, recorders: Recorders) {
    if (!ui.active || !ui.paused) return
    when (ui.mode) {
        RecordMode.Voice -> recorders.voice.resume()
        RecordMode.Circle -> recorders.circle.resume()
    }
    ui.markPaused(false)
}

internal fun cancel(ui: RecordingUi, recorders: Recorders, scope: CoroutineScope) {
    val mode = ui.mode
    ui.reset()
    when (mode) {
        RecordMode.Voice -> recorders.voice.cancel()
        RecordMode.Circle -> scope.launch { recorders.circle.close() }
    }
}

/**
 * A locked voice note left behind when the chat was closed is still recording: coming back
 * shows it again, locked, as it was.
 */
@Composable
internal fun RestoreRecording(ui: RecordingUi, recorders: Recorders) {
    val active by recorders.voice.active.collectAsStateWithLifecycle()
    val paused by recorders.voice.paused.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(active) {
        if (active && !ui.active && recorders.voice.owner == recorders.conversationId) {
            ui.mode = RecordMode.Voice
            ui.active = true
            ui.locked = true
            ui.paused = paused
        }
    }
    // A held note cannot outlive its screen: the finger has nothing left to let go of. Nor can a
    // circle, whose camera belongs to the screen.
    DisposableEffect(ui) {
        onDispose {
            when {
                !ui.active -> Unit
                ui.mode == RecordMode.Circle -> recorders.circle.close()
                !ui.locked -> recorders.voice.cancel()
            }
        }
    }
}

/** Replaces the text field while recording: a pulsing dot, the time, and how to cancel. */
@Composable
internal fun RecordingBar(ui: RecordingUi, recorders: Recorders, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val voiceElapsed by recorders.voice.elapsedMillis.collectAsStateWithLifecycle()
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
            val pulse = if (ui.paused) 0f else 0.45f * sin(phase * 2f * PI).toFloat()
            drawCircle(danger.copy(alpha = 0.55f + pulse))
        }
        // A voice note's own clock goes on in the background; a circle is timed here.
        val elapsed = if (ui.mode == RecordMode.Voice) voiceElapsed else ui.elapsed(now)
        Text(formatDuration(elapsed), style = Blesk.type.bodyStrong)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (!ui.locked) SlideToCancel(phase, Modifier.graphicsLayer {
                translationX = ui.dragX / 2f
                alpha = 1f + ui.dragX / CANCEL_FADE
            })
        }
        if (ui.locked) {
            IconButton(BleskIcons.Trash, stringResource(R.string.media_cancel), { cancel(ui, recorders, scope) })
            // Set aside by the app going away, a recording waits to be picked up again.
            if (ui.paused) IconButton(BleskIcons.Mic, stringResource(R.string.media_record_resume), { resume(ui, recorders) })
            IconButton(BleskIcons.Stop, stringResource(R.string.media_stop), { stop(ui, recorders, scope) })
            SendCapsule(onSend = { finish(ui, recorders, scope) }, modifier = Modifier.padding(end = 6.dp))
        }
    }
}

/** «‹‹ влево — отмена», the arrows running to the left so the way is plain. */
@Composable
private fun SlideToCancel(phase: Float, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val tint = Blesk.colors.textSecondary
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            repeat(ARROWS) { i ->
                // Each arrow lights up in turn, from the right, as if running left.
                val lit = ((phase * ARROWS).toInt() % ARROWS) == ARROWS - 1 - i
                Icon(
                    BleskIcons.Back,
                    contentDescription = null,
                    size = 16.dp,
                    tint = if (lit) Blesk.colors.textPrimary else tint.copy(alpha = ARROW_DIM),
                )
            }
        }
        Text(stringResource(R.string.media_slide_cancel_arrows), style = Blesk.type.label, color = tint, maxLines = 1)
    }
}

private const val ARROWS = 3
private const val ARROW_DIM = 0.45f
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
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(BleskIcons.Lock, contentDescription = null, size = 18.dp)
            Icon(BleskIcons.ArrowUp, contentDescription = null, size = 14.dp, tint = Blesk.colors.textSecondary)
        }
    }
}

/** The camera inside the circle while a round video is being recorded, with a ring for the time left. */
@Composable
internal fun CircleOverlay(ui: RecordingUi, recorders: Recorders) {
    val draft = ui.draft as? RecordDraft.Circle
    if (draft != null && !ui.active) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = PREVIEW_DIM)).blockTaps(), contentAlignment = Alignment.Center) {
            CirclePreview(draft, ui, recorders)
        }
        return
    }
    val camera = recorders.circle
    val request by camera.preview.collectAsStateWithLifecycle()
    val front by camera.front.collectAsStateWithLifecycle()
    val light by camera.light.collectAsStateWithLifecycle()
    val torchLevels by camera.torchLevels.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val hints = rememberRecordHints()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(ui.active) {
        while (ui.active) {
            now = System.currentTimeMillis()
            delay(TICK_MILLIS)
        }
    }
    val glow = light && front
    if (glow) FullBrightness()
    val progress = (ui.elapsed(now).toFloat() / CircleRecorder.MAX_DURATION_MILLIS).coerceIn(0f, 1f)
    val ring = Blesk.colors.light
    // The front camera has no torch: the screen itself, warm white and as bright as it goes, lights the face.
    val backdrop = if (glow) WarmWhite else Color.Black.copy(alpha = 0.55f)
    Box(Modifier.fillMaxSize().background(backdrop), contentAlignment = Alignment.Center) {
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
        // The lower half of the screen, so the controls hang from the circle however many there are.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.5f).padding(top = ControlsTop),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(CircleShape).background(Blesk.colors.surface)) {
                    IconButton(BleskIcons.Flip, stringResource(R.string.media_circle_flip), { camera.flip() })
                }
                LightToggle(light, onToggle = { camera.setLight(!light) })
            }
            if (light && !front && torchLevels > 1) TorchStrength(torchLevels, camera::setTorchLevel)
            if (ui.locked) {
                // Locked, the circle has its own «Удалить», «Стоп» and «Отправить» right here, by the circle.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(CircleShape).background(Blesk.colors.surface)) {
                        IconButton(BleskIcons.Trash, stringResource(R.string.media_cancel), { cancel(ui, recorders, scope) })
                    }
                    Box(Modifier.clip(CircleShape).background(Blesk.colors.surface)) {
                        IconButton(BleskIcons.Stop, stringResource(R.string.media_stop), { stop(ui, recorders, scope) })
                    }
                    SendCapsule(onSend = { finish(ui, recorders, scope) }, label = true)
                }
            } else if (hints.showing) {
                Text(
                    text = stringResource(R.string.media_record_gestures),
                    style = Blesk.type.label,
                    color = Blesk.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = Blesk.dimens.gutter)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Blesk.colors.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** «Свет» on a capsule: off until turned on, then remembered for the next circle. */
@Composable
private fun LightToggle(on: Boolean, onToggle: () -> Unit) {
    val haptics = rememberHaptics()
    val action = stringResource(if (on) R.string.media_light_off else R.string.media_light_on)
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (on) Blesk.colors.accent else Blesk.colors.surface)
            .semantics { stateDescription = action }
            .toggleable(value = on, role = Role.Switch) {
                haptics.toggle(!on)
                onToggle()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = if (on) Blesk.colors.onAccent else Blesk.colors.textPrimary
        Icon(BleskIcons.Light, contentDescription = null, tint = tint, size = 20.dp)
        Text(stringResource(R.string.media_light), style = Blesk.type.bodyStrong, color = tint)
    }
}

/** How bright the back camera's torch shines, where the phone lets it be set. */
@Composable
private fun TorchStrength(levels: Int, onLevel: (Int) -> Unit) {
    var value by remember { mutableFloatStateOf(1f) }
    Box(Modifier.width(TorchWidth).clip(CircleShape).background(Blesk.colors.surface)) {
        Slider(
            title = stringResource(R.string.media_light_strength),
            value = value,
            onValueChange = {
                value = it
                onLevel((it * levels).roundToInt().coerceIn(1, levels))
            },
        )
    }
}

/** Turns the screen up to full while shown and gives the brightness back afterwards. */
@Composable
private fun FullBrightness() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        val before = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { window.attributes = window.attributes.apply { screenBrightness = before } }
    }
}

/** Light for a face: white with a little warmth, kinder to skin than a cold screen. */
private val WarmWhite = Color(0xFFFFF3E2)
private val TorchWidth = 280.dp

private const val PREVIEW_DIM = 0.7f

/** Under the circle, clear of the ring. */
private val ControlsTop = 168.dp
