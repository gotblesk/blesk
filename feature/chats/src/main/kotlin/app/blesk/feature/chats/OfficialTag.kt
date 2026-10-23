package app.blesk.feature.chats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.blesk.core.design.component.Tag

/** «Официальный»: the server's own channel, marked by its owner, wherever it is named. */
@Composable
internal fun OfficialTag(modifier: Modifier = Modifier) {
    Tag(stringResource(R.string.official_tag), modifier)
}
