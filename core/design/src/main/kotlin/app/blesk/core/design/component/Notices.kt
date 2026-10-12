package app.blesk.core.design.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blesk.core.design.R
import app.blesk.core.design.glass.lens
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One short notice: a line of text, and for an undoable action the action itself. */
class Notice internal constructor(val text: String, internal val undoable: Undoable?)

internal class Undoable(val hidden: Collection<String>, val commit: () -> Unit)

/**
 * Short notices over the bottom lens: «Скопировано», errors, and «Отменить». Only one is shown
 * at a time; a new one replaces the old.
 *
 * Instead of «Are you sure?» an action is announced with «Отменить» and carried out only once
 * the five seconds run out. Until then the thing it touches is listed in [hidden], so screens
 * can already show it gone. Replacing an undoable notice carries its action out at once.
 * The timer runs in [scope], so leaving the screen never loses or repeats the action.
 */
@Stable
class Notices(private val scope: CoroutineScope) {
    var current: Notice? by mutableStateOf(null)
        private set

    /** Keys of things whose removal is waiting out its «Отменить». */
    val hidden: SnapshotStateSet<String> = SnapshotStateSet()

    private var timer: Job? = null

    fun show(text: String) = post(Notice(text, null), SHORT_MILLIS)

    /** Announces [commit] with «Отменить»; [hidden] keeps the affected item out of sight meanwhile. */
    fun undoable(text: String, hidden: Collection<String> = emptyList(), commit: () -> Unit) {
        this.hidden.addAll(hidden)
        post(Notice(text, Undoable(hidden, commit)), UNDO_MILLIS)
    }

    fun undo() {
        val notice = current ?: return
        timer?.cancel()
        notice.undoable?.hidden?.let(hidden::removeAll)
        current = null
    }

    private fun post(notice: Notice, millis: Long) {
        timer?.cancel()
        current?.let(::finish)
        current = notice
        timer = scope.launch {
            delay(millis)
            finish(notice)
            if (current === notice) current = null
        }
    }

    private fun finish(notice: Notice) {
        val action = notice.undoable ?: return
        action.commit()
        hidden.removeAll(action.hidden)
    }

    private companion object {
        const val SHORT_MILLIS = 2_500L
        const val UNDO_MILLIS = 5_000L
    }
}

val LocalNotices = staticCompositionLocalOf<Notices?> { null }

/**
 * Draws the current notice above the bottom lens. [lift] is how far above the bottom edge the
 * lens reaches on this screen.
 */
@Composable
fun NoticeHost(notices: Notices, modifier: Modifier = Modifier, lift: Dp = 96.dp) {
    val motion = Blesk.motion
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(bottom = lift, start = Blesk.dimens.gutter, end = Blesk.dimens.gutter),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedContent(
            targetState = notices.current,
            transitionSpec = {
                (slideInVertically(motion.settle()) { it / 2 } + fadeIn(motion.press()))
                    .togetherWith(slideOutVertically(motion.transition()) { it / 2 } + fadeOut(motion.press()))
            },
            label = "notice",
        ) { notice ->
            if (notice != null) NoticeCapsule(notice, onUndo = notices::undo)
        }
    }
}

@Composable
private fun NoticeCapsule(notice: Notice, onUndo: () -> Unit) {
    var undone by remember(notice) { mutableStateOf(false) }
    val undoLabel = stringResource(R.string.design_undo)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .lens(CircleShape, elevation = 10.dp)
            .padding(start = 18.dp, end = if (notice.undoable != null) 6.dp else 18.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(notice.text, style = Blesk.type.label, color = Blesk.colors.textPrimary, maxLines = 2)
        if (notice.undoable != null) {
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, enabled = !undone) {
                        undone = true
                        onUndo()
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(undoLabel, style = Blesk.type.bodyStrong, color = Blesk.colors.textPrimary)
            }
        }
    }
}

/** Shows [text] through the app's notices, if the app has them. */
@Composable
fun rememberNotify(): (String) -> Unit {
    val notices = LocalNotices.current
    return { text -> notices?.show(text) }
}
