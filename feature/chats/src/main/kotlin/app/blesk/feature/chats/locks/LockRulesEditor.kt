package app.blesk.feature.chats.locks

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.LockCode
import app.blesk.core.data.settings.LockCounter
import app.blesk.core.data.settings.LockListing
import app.blesk.core.data.settings.LockNotify
import app.blesk.core.data.settings.LockOverrides
import app.blesk.core.data.settings.LockPreview
import app.blesk.core.data.settings.LockRelock
import app.blesk.core.data.settings.LockRules
import app.blesk.core.data.settings.LockUnlock
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.MenuSheet
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import kotlinx.collections.immutable.toImmutableList

/** One setting of locks: its title and its choices, in the order of the enum. */
private class Setting<T : Enum<T>>(
    @param:StringRes val title: Int,
    val labels: List<Int>,
    val values: List<T>,
    val read: (LockRules) -> T,
    val readOwn: (LockOverrides) -> T?,
    val write: (LockRules, T) -> LockRules,
    val writeOwn: (LockOverrides, T?) -> LockOverrides,
)

private val SETTINGS: List<Setting<*>> = listOf(
    Setting(
        R.string.lock_counter, listOf(R.string.lock_counter_number, R.string.lock_counter_dot, R.string.lock_counter_hidden),
        LockCounter.entries, { it.counter }, { it.counter }, { r, v -> r.copy(counter = v) }, { o, v -> o.copy(counter = v) },
    ),
    Setting(
        R.string.lock_preview, listOf(R.string.lock_preview_hidden, R.string.lock_preview_shown),
        LockPreview.entries, { it.preview }, { it.preview }, { r, v -> r.copy(preview = v) }, { o, v -> o.copy(preview = v) },
    ),
    Setting(
        R.string.lock_notify, listOf(R.string.lock_notify_full, R.string.lock_notify_anonymous, R.string.lock_notify_none),
        LockNotify.entries, { it.notify }, { it.notify }, { r, v -> r.copy(notify = v) }, { o, v -> o.copy(notify = v) },
    ),
    Setting(
        R.string.lock_search, listOf(R.string.lock_hide, R.string.lock_show),
        LockListing.entries, { it.search }, { it.search }, { r, v -> r.copy(search = v) }, { o, v -> o.copy(search = v) },
    ),
    Setting(
        R.string.lock_share, listOf(R.string.lock_hide, R.string.lock_show),
        LockListing.entries, { it.share }, { it.share }, { r, v -> r.copy(share = v) }, { o, v -> o.copy(share = v) },
    ),
    Setting(
        R.string.lock_relock, listOf(R.string.lock_relock_exit, R.string.lock_relock_minute, R.string.lock_relock_background),
        LockRelock.entries, { it.relock }, { it.relock }, { r, v -> r.copy(relock = v) }, { o, v -> o.copy(relock = v) },
    ),
    Setting(
        R.string.lock_unlock, listOf(R.string.lock_unlock_any, R.string.lock_unlock_code),
        LockUnlock.entries, { it.unlock }, { it.unlock }, { r, v -> r.copy(unlock = v) }, { o, v -> o.copy(unlock = v) },
    ),
    Setting(
        R.string.lock_code, listOf(R.string.lock_code_app, R.string.lock_code_separate),
        LockCode.entries, { it.code }, { it.code }, { r, v -> r.copy(code = v) }, { o, v -> o.copy(code = v) },
    ),
)

/** The general rules of locks, each one segmented control. */
@Composable
fun LockRulesEditor(rules: LockRules, onChange: (LockRules) -> Unit) {
    SETTINGS.forEach { setting -> GeneralRow(setting, rules, onChange) }
}

@Composable
private fun <T : Enum<T>> GeneralRow(setting: Setting<T>, rules: LockRules, onChange: (LockRules) -> Unit) {
    Title(setting.title)
    Segmented(
        options = setting.labels.map { stringResource(it) }.toImmutableList(),
        selected = setting.values.indexOf(setting.read(rules)),
        onSelect = { onChange(setting.write(rules, setting.values[it])) },
    )
}

/** One chat's rules: «Как в общих настройках» first, then each choice. */
@Composable
private fun <T : Enum<T>> OwnRow(setting: Setting<T>, own: LockOverrides, onChange: (LockOverrides) -> Unit) {
    Title(setting.title)
    val general = stringResource(R.string.lock_as_general)
    Segmented(
        options = (listOf(general) + setting.labels.map { stringResource(it) }).toImmutableList(),
        selected = setting.readOwn(own)?.let { setting.values.indexOf(it) + 1 } ?: 0,
        onSelect = { onChange(setting.writeOwn(own, if (it == 0) null else setting.values[it - 1])) },
    )
}

@Composable
private fun Title(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = Blesk.type.bodyStrong,
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 12.dp),
    )
}

/**
 * «Замок» in a chat's details: lock or unlock it, and give it rules of its own; every setting
 * starts as «Как в общих настройках».
 */
@Composable
fun ChatLockSheet(
    title: String,
    locked: Boolean,
    own: LockOverrides,
    onLock: (Boolean) -> Unit,
    onOwn: (LockOverrides) -> Unit,
    onDismiss: () -> Unit,
) {
    MenuSheet(title = title, onDismiss = onDismiss) {
        Card {
            Column(Modifier.heightIn(max = SheetMax).verticalScroll(rememberScrollState())) {
                ToggleRow(
                    title = stringResource(R.string.lock_add),
                    subtitle = stringResource(R.string.lock_add_hint),
                    icon = BleskIcons.Lock,
                    checked = locked,
                    onCheckedChange = onLock,
                )
                if (locked) SETTINGS.forEach { setting -> OwnRow(setting, own, onOwn) }
            }
        }
    }
}

/** «Замок» as a row in a chat's details, with what it is now under the title. */
@Composable
fun ChatLockRow(locked: Boolean, onClick: () -> Unit) {
    ListRow(
        title = stringResource(R.string.lock_short),
        subtitle = stringResource(if (locked) R.string.lock_state_on else R.string.lock_state_off),
        icon = BleskIcons.Lock,
        onClick = onClick,
    )
}

private val SheetMax = 520.dp

/** The lock of the chat whose details are open, given by the screen around them. */
class ChatLockControls(
    val title: String,
    val locked: Boolean,
    val own: LockOverrides,
    val onLock: (Boolean) -> Unit,
    val onOwn: (LockOverrides) -> Unit,
)

val LocalChatLockControls = compositionLocalOf<ChatLockControls?> { null }

/** «Замок» in a chat's details and its sheet; nothing where the chat cannot be locked. */
@Composable
fun ChatLockEntry() {
    val controls = LocalChatLockControls.current ?: return
    var open by remember { mutableStateOf(false) }
    ChatLockRow(controls.locked) { open = true }
    if (open) {
        ChatLockSheet(controls.title, controls.locked, controls.own, controls.onLock, controls.onOwn, onDismiss = { open = false })
    }
}
