package app.blesk.feature.chats

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import app.blesk.shared.protocol.LinkPreview
import java.net.URI
import java.util.Base64

/**
 * The card of a link under a message: picture, title, a few lines of description and the site.
 * Matte, never glinting: the page itself is not end-to-end anything.
 */
@Composable
internal fun LinkCard(link: LinkPreview, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val image = remember(link.image) { link.image?.let(::decodeImage) }
    val host = remember(link.url) { runCatching { URI(link.url).host?.removePrefix("www.") }.getOrNull() }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .widthIn(min = 220.dp)
            .heightIn(min = Blesk.dimens.touchTarget)
            .clip(shape)
            .background(Blesk.colors.textPrimary.copy(alpha = CARD_ALPHA))
            .clickable(onClickLabel = stringResource(R.string.chat_link_open), role = Role.Button) { uriHandler.openUri(link.url) }
            .clearAndSetSemantics { contentDescription = listOfNotNull(link.title, host).joinToString(", ") },
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (host != null) {
                Text(host, style = Blesk.type.caption, color = Blesk.colors.textTertiary, maxLines = 1)
            }
            Text(link.title, style = Blesk.type.bodyStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (link.description.isNotEmpty()) {
                Text(
                    link.description,
                    style = Blesk.type.caption,
                    color = Blesk.colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun decodeImage(base64: String): ImageBitmap? = runCatching {
    val bytes = Base64.getDecoder().decode(base64)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

private const val CARD_ALPHA = 0.06f
