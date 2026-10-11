package app.blesk.core.data

import app.blesk.core.data.settings.IncomingNote
import kotlinx.coroutines.flow.SharedFlow

/** New messages from other people, for notifications. */
interface IncomingMessages {
    val incoming: SharedFlow<IncomingNote>
}
