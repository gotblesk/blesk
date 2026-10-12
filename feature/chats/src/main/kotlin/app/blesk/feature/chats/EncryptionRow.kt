package app.blesk.feature.chats

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk

/**
 * Says in words whether a chat is end-to-end encrypted, so the glint is never the only sign:
 * someone who cannot see it, or does not know what it means, still finds out here.
 */
@Composable
internal fun EncryptionRow(protected: Boolean, modifier: Modifier = Modifier) {
    Card(modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = Blesk.dimens.sectionGap)) {
        ListRow(
            title = stringResource(if (protected) R.string.info_e2ee else R.string.info_not_e2ee),
            subtitle = stringResource(if (protected) R.string.info_e2ee_hint else R.string.info_not_e2ee_hint),
            icon = if (protected) BleskIcons.Lock else BleskIcons.Server,
            showChevron = false,
        )
    }
}
