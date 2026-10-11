package app.blesk.core.calls

import android.content.Context
import android.content.Intent
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.e2ee.E2EEOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** One person in a call as the screen draws them. */
data class CallMember(
    val id: String,
    val local: Boolean,
    val speaking: Boolean,
    val micOn: Boolean,
    val camera: VideoTrack?,
    val screen: VideoTrack?,
)

data class MediaState(
    val connected: Boolean = false,
    val reconnecting: Boolean = false,
    val members: List<CallMember> = emptyList(),
    val micOn: Boolean = true,
    val cameraOn: Boolean = false,
    val speakerOn: Boolean = false,
    val sharing: Boolean = false,
    /** Someone else is in the room. */
    val othersJoined: Boolean = false,
)

/** What the call logic needs from the media side; faked in tests. */
interface MediaSession {
    val state: StateFlow<MediaState>

    var onDisconnected: () -> Unit

    suspend fun connect(url: String, token: String, key: String, video: Boolean, http: OkHttpClient?): Boolean

    fun disconnect()
}

/**
 * The media side of a call: a LiveKit room whose audio and video frames are end-to-end
 * encrypted by WebRTC's frame cryptor with a key that only the participants know.
 */
class CallMedia(private val context: Context, private val scope: CoroutineScope) : MediaSession {
    private val mutable = MutableStateFlow(MediaState())
    override val state: StateFlow<MediaState> = mutable.asStateFlow()

    private var room: Room? = null
    private var events: Job? = null

    /** Called when the room ends from the other side or the network gives up. */
    override var onDisconnected: () -> Unit = {}

    /** The room, for the screen to attach video renderers. */
    val currentRoom: Room? get() = room

    override suspend fun connect(url: String, token: String, key: String, video: Boolean, http: OkHttpClient?): Boolean {
        disconnect()
        val created = LiveKit.create(
            appContext = context,
            options = RoomOptions(adaptiveStream = true, dynacast = true, e2eeOptions = E2EEOptions(key)),
            overrides = LiveKitOverrides(okHttpClient = http),
        )
        room = created
        events = scope.launch { created.events.collect { event -> onEvent(event) } }
        return try {
            created.connect(url, token)
            created.localParticipant.setMicrophoneEnabled(true)
            if (video) created.localParticipant.setCameraEnabled(true)
            mutable.update { it.copy(connected = true, micOn = true, cameraOn = video) }
            setSpeaker(video)
            refresh()
            true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            disconnect()
            false
        }
    }

    override fun disconnect() {
        events?.cancel()
        events = null
        room?.let {
            it.disconnect()
            it.release()
        }
        room = null
        mutable.value = MediaState()
    }

    suspend fun setMic(on: Boolean) {
        room?.localParticipant?.setMicrophoneEnabled(on) ?: return
        mutable.update { it.copy(micOn = on) }
        refresh()
    }

    suspend fun setCamera(on: Boolean) {
        room?.localParticipant?.setCameraEnabled(on) ?: return
        mutable.update { it.copy(cameraOn = on) }
        refresh()
    }

    fun flipCamera() {
        val track = room?.localParticipant?.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack ?: return
        val front = track.options.position == CameraPosition.FRONT
        track.switchCamera(position = if (front) CameraPosition.BACK else CameraPosition.FRONT)
    }

    fun setSpeaker(on: Boolean) {
        val audio = room?.audioSwitchHandler ?: return
        val wanted = audio.availableAudioDevices.firstOrNull {
            if (on) it is AudioDevice.Speakerphone else it !is AudioDevice.Speakerphone
        }
        audio.selectDevice(wanted)
        mutable.update { it.copy(speakerOn = on) }
    }

    /** [permission] is the result of the system's screen capture dialog; null stops sharing. */
    suspend fun shareScreen(permission: Intent?) {
        val local = room?.localParticipant ?: return
        if (permission == null) {
            local.setScreenShareEnabled(false)
        } else {
            local.setScreenShareEnabled(true, ScreenCaptureParams(permission, onStop = { scope.launch { shareScreen(null) } }))
        }
        mutable.update { it.copy(sharing = permission != null) }
        refresh()
    }

    private fun onEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.Reconnecting -> mutable.update { it.copy(reconnecting = true) }
            is RoomEvent.Reconnected -> mutable.update { it.copy(reconnecting = false) }
            is RoomEvent.Disconnected -> {
                mutable.update { it.copy(connected = false) }
                onDisconnected()
            }
            else -> Unit
        }
        refresh()
    }

    private fun refresh() {
        val current = room ?: return
        val local = current.localParticipant
        val members = listOf(member(local, local = true)) + current.remoteParticipants.values.map { member(it, local = false) }
        mutable.update { it.copy(members = members, othersJoined = current.remoteParticipants.isNotEmpty()) }
    }

    private fun member(participant: Participant, local: Boolean) = CallMember(
        id = participant.identity?.value.orEmpty(),
        local = local,
        speaking = participant.isSpeaking,
        micOn = participant.getTrackPublication(Track.Source.MICROPHONE)?.muted == false,
        camera = participant.videoOf(Track.Source.CAMERA),
        screen = participant.videoOf(Track.Source.SCREEN_SHARE),
    )

    private fun Participant.videoOf(source: Track.Source): VideoTrack? =
        getTrackPublication(source)?.takeIf { !it.muted }?.track as? VideoTrack
}
