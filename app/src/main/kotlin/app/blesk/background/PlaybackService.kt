package app.blesk.background

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import app.blesk.BleskApplication
import app.blesk.MainActivity

/**
 * Voice notes and songs go on with the app in the background: the system shows the player in the
 * shade and on the lock screen, with play, pause and the next voice note.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = (application as BleskApplication).container.audioPlayer.player
        val open = PendingIntent.getActivity(this, 0, MainActivity.open(this, null), PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        /** Connects to the service once playback starts, which lets it put the player in the shade. */
        fun connect(context: Context) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            MediaController.Builder(context, token).buildAsync()
        }
    }
}
