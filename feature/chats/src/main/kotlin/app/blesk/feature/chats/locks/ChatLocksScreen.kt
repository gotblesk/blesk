package app.blesk.feature.chats.locks

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.LockCode
import app.blesk.core.data.settings.LockRules
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R

class ChatLocksActions(
    val onRules: (LockRules) -> Unit,
    val onArchive: (Boolean) -> Unit,
    /** Making or changing the separate code for locks. */
    val onSeparateCode: () -> Unit,
    val onBack: () -> Unit,
)

/**
 * «Настройки → Приватность и безопасность → Замки на чаты»: how locked chats behave unless a chat
 * has rules of its own, the archive's lock, and the separate code.
 */
@Composable
fun ChatLocksScreen(
    rules: LockRules,
    lockedChats: Int,
    archiveLocked: Boolean,
    separateCodeSet: Boolean,
    actions: ChatLocksActions,
    modifier: Modifier = Modifier,
) {
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    ScrollScreen(title = stringResource(R.string.locks_title), onBack = actions.onBack, modifier = modifier) {
        Text(
            stringResource(R.string.locks_hint),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).padding(bottom = 8.dp),
        )
        Card(pad) {
            ListRow(
                title = if (lockedChats > 0) {
                    pluralStringResource(R.plurals.locks_count, lockedChats, lockedChats)
                } else {
                    stringResource(R.string.locks_none)
                },
                icon = BleskIcons.Lock,
                showChevron = false,
            )
            ToggleRow(
                title = stringResource(R.string.lock_archive),
                subtitle = stringResource(R.string.lock_archive_hint),
                icon = BleskIcons.Archive,
                checked = archiveLocked,
                onCheckedChange = actions.onArchive,
            )
        }
        SectionHeader(stringResource(R.string.locks_general))
        Card(pad) {
            LockRulesEditor(rules, actions.onRules)
            if (rules.code == LockCode.Separate) {
                ListRow(
                    title = stringResource(R.string.locks_separate_code),
                    subtitle = stringResource(
                        if (separateCodeSet) R.string.locks_separate_code_set else R.string.locks_separate_code_missing,
                    ),
                    icon = BleskIcons.Lock,
                    onClick = actions.onSeparateCode,
                )
            }
        }
    }
}
