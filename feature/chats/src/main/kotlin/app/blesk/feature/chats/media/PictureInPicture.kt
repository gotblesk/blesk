package app.blesk.feature.chats.media

import android.util.Rational
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the activity offers a playing video: shrinking into a corner of the screen when the
 * person leaves the app. [active] is true while it is in that corner, where only the picture shows.
 */
class PictureInPicture(val active: State<Boolean>, val onVideo: (aspect: Rational?) -> Unit)

val LocalPictureInPicture = staticCompositionLocalOf<PictureInPicture?> { null }
