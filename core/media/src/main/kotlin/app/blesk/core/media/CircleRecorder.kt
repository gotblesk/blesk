package app.blesk.core.media

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.blesk.core.data.model.Attachment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Round video messages: the front camera, recorded at SD quality. The circle is a crop made
 * when playing; the file itself is an ordinary video.
 */
class CircleRecorder(private val context: Context, private val vault: MediaVault) {
    private val surface = MutableStateFlow<SurfaceRequest?>(null)
    private var provider: ProcessCameraProvider? = null
    private var owner: LifecycleOwner? = null
    private var previewCase: Preview? = null
    private var capture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val frontState = MutableStateFlow(true)
    private val lightState = MutableStateFlow(prefs.getBoolean(KEY_LIGHT, false))
    private val torchLevelsState = MutableStateFlow(0)
    private var torchLevel = 0
    private var recording: Recording? = null
    private var output: File? = null
    private var finished: CompletableDeferred<Boolean>? = null

    /** The camera preview to show while the circle is open. */
    val preview: StateFlow<SurfaceRequest?> = surface.asStateFlow()

    /** Which camera records: the front one lights the face with the screen, the back one with its torch. */
    val front: StateFlow<Boolean> = frontState.asStateFlow()

    /** «Свет» while recording: off until turned on, then remembered. */
    val light: StateFlow<Boolean> = lightState.asStateFlow()

    /** How many steps of brightness the back camera's torch has; 0 or 1 when it is only on or off. */
    val torchLevels: StateFlow<Int> = torchLevelsState.asStateFlow()

    fun setLight(on: Boolean) {
        lightState.value = on
        prefs.edit().putBoolean(KEY_LIGHT, on).apply()
        applyTorch()
    }

    /** Brightness of the torch from 1 to [torchLevels], where the phone allows it (Android 13 and later). */
    fun setTorchLevel(level: Int) {
        torchLevel = level
        applyTorch()
    }

    private fun applyTorch() {
        val control = camera?.cameraControl ?: return
        val on = lightState.value && !frontState.value
        runCatching { control.enableTorch(on) }
        val max = torchLevelsState.value
        if (on && max > 1 && torchLevel > 0) runCatching { control.setTorchStrengthLevel(torchLevel.coerceIn(1, max)) }
    }

    suspend fun open(owner: LifecycleOwner) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surface.value = it } }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build()
        val videoCapture = VideoCapture.withOutput(recorder)
        frontState.value = true
        provider = cameraProvider
        this.owner = owner
        previewCase = preview
        capture = videoCapture
        bind()
    }

    /** Front to back and back again, without a break in the recording: it is persistent, so rebinding keeps it going. */
    fun flip() {
        if (provider == null) return
        frontState.value = !frontState.value
        bind()
    }

    private fun bind() {
        val cameraProvider = provider ?: return
        val lifecycle = owner ?: return
        val selector = if (frontState.value) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val shown = previewCase ?: return
        val recorded = capture ?: return
        cameraProvider.unbindAll()
        camera = runCatching { cameraProvider.bindToLifecycle(lifecycle, selector, shown, recorded) }.getOrNull()
        val info = camera?.cameraInfo
        torchLevelsState.value = if (info != null && info.hasFlashUnit() && info.isTorchStrengthSupported) info.maxTorchStrengthLevel else 0
        if (torchLevel == 0) torchLevel = torchLevelsState.value
        applyTorch()
    }

    @OptIn(ExperimentalPersistentRecording::class)
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val videoCapture = capture ?: return false
        val file = vault.newFile("mp4")
        val done = CompletableDeferred<Boolean>()
        recording = videoCapture.output
            .prepareRecording(context, FileOutputOptions.Builder(file).setDurationLimitMillis(MAX_DURATION_MILLIS).build())
            .withAudioEnabled()
            // Survives the camera closing when the app is minimized: the circle waits on pause instead of ending.
            .asPersistentRecording()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) done.complete(event.error in KEPT)
            }
        output = file
        finished = done
        return true
    }

    /** Stops recording and returns the circle, or null if it failed or was too short. */
    suspend fun finish(): Attachment.Circle? {
        val file = output ?: return null
        recording?.stop()
        val ok = finished?.await() ?: false
        recording = null
        output = null
        val video = if (ok) vault.describeVideo(file) else null
        if (video == null || video.durationMillis < MIN_DURATION_MILLIS) {
            file.delete()
            video?.let { File(it.thumbnail).delete() }
            return null
        }
        return Attachment.Circle(file.path, video.thumbnail, video.durationMillis)
    }

    fun pause() {
        recording?.pause()
    }

    fun resume() {
        recording?.resume()
    }

    fun cancel() {
        recording?.stop()
        recording = null
        output?.delete()
        output = null
    }

    fun close() {
        cancel()
        provider?.unbindAll()
        provider = null
        owner = null
        previewCase = null
        capture = null
        camera = null
        surface.value = null
    }

    companion object {
        const val MAX_DURATION_MILLIS = 60_000L
        private const val PREFS = "circle"
        private const val KEY_LIGHT = "light"

        /** Endings after which the file holds everything recorded so far. */
        private val KEPT = setOf(
            VideoRecordEvent.Finalize.ERROR_NONE,
            VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
            VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,
        )
        private const val MIN_DURATION_MILLIS = 700L
    }
}
