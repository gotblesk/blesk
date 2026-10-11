package app.blesk.feature.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.RendererCommon

/** Draws one LiveKit video track; the renderer is released with the composable. */
@Composable
internal fun VideoView(room: Room, track: VideoTrack, modifier: Modifier = Modifier, mirror: Boolean = false, fill: Boolean = true) {
    val context = LocalContext.current
    val renderer = remember(room) {
        TextureViewRenderer(context).also { view ->
            room.initVideoRenderer(view)
            view.setEnableHardwareScaler(true)
        }
    }
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    DisposableEffect(track, renderer) {
        track.addRenderer(renderer)
        onDispose { track.removeRenderer(renderer) }
    }
    AndroidView(
        factory = { renderer },
        update = {
            it.setMirror(mirror)
            it.setScalingType(if (fill) RendererCommon.ScalingType.SCALE_ASPECT_FILL else RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        },
        modifier = modifier,
    )
}
