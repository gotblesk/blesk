package app.blesk.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.Profile
import app.blesk.core.design.component.ActionCluster
import app.blesk.core.design.component.ClusterAction
import app.blesk.core.design.component.DemoTag
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.ProfileHero
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk

/** The things the «Ты» header can do. */
class YouHeaderActions(val onEdit: () -> Unit, val onQr: () -> Unit, val onShare: () -> Unit)

/**
 * The top of «Ты»: a header a third of the screen tall that melts into the background, the
 * avatar sitting on its lower edge, then the name and the action cluster. While scrolling the
 * header moves slower than the page and blurs, and the avatar shrinks on its way up into the
 * top lens.
 */
@Composable
fun YouHeader(
    profile: Profile,
    photo: ImageBitmap?,
    header: HeaderStyle,
    headerPhoto: ImageBitmap?,
    scroll: () -> Int,
    actions: YouHeaderActions,
    demo: Boolean,
    unnamed: String,
    labels: YouHeaderLabels,
) {
    val key = profile.username.ifBlank { profile.name }
    val name = profile.name.ifBlank { unnamed }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileHero(name = name, key = key, photo = photo, header = header, headerPhoto = headerPhoto, scroll = scroll)
        Row(
            modifier = Modifier.padding(top = 10.dp, start = Blesk.dimens.gutter, end = Blesk.dimens.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = name,
                style = Blesk.type.title,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (demo) DemoTag()
        }
        if (profile.username.isNotBlank()) {
            Text("@${profile.username}", style = Blesk.type.label, color = Blesk.colors.textSecondary)
        }
        if (profile.bio.isNotBlank()) {
            Text(
                text = profile.bio,
                color = Blesk.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter * 2).padding(top = 6.dp),
            )
        }
        ActionCluster(
            listOf(
                ClusterAction(BleskIcons.Edit, labels.edit, actions.onEdit),
                ClusterAction(BleskIcons.Qr, labels.qr, actions.onQr),
                ClusterAction(BleskIcons.Share, labels.share, actions.onShare),
            ),
            Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 16.dp),
        )
    }
}

/** Labels of the header, read from resources by the caller. */
class YouHeaderLabels(val edit: String, val qr: String, val share: String)
