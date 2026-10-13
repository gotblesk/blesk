package app.blesk.feature.chats

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.blesk.core.data.settings.GestureHint

/** The words of each gesture hint. */
@Composable
fun hintText(hint: GestureHint): String = stringResource(
    when (hint) {
        GestureHint.Reply -> R.string.hint_reply
        GestureHint.DoubleTap -> R.string.hint_double_tap
        GestureHint.RecordLock -> R.string.hint_record_lock
        GestureHint.PinnedDrag -> R.string.hint_pinned_drag
        GestureHint.ArchivePull -> R.string.hint_archive_pull
        GestureHint.CallCapsule -> R.string.hint_call_capsule
    },
)
