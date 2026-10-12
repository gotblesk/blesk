package app.blesk.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk

/** Your code for someone to scan: it opens a chat with you. */
@Composable
fun MyQrScreen(username: String, onBack: () -> Unit, modifier: Modifier = Modifier, linkKey: String? = null) {
    ScrollScreen(title = stringResource(R.string.my_qr_title), onBack = onBack, modifier = modifier) {
        if (username.isBlank()) {
            Text(
                text = stringResource(R.string.my_qr_no_name),
                color = Blesk.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 16.dp),
            )
            return@ScrollScreen
        }
        val label = stringResource(R.string.my_qr_label, username)
        Box(
            Modifier
                .padding(horizontal = Blesk.dimens.gutter * 2, vertical = 16.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Blesk.dimens.cardRadius))
                .background(Color.White)
                .padding(20.dp)
                .semantics { contentDescription = label },
        ) {
            QrCode(profileLink(username, linkKey), Color.Black, Modifier.fillMaxWidth().aspectRatio(1f))
        }
        Text(
            text = "@$username",
            style = Blesk.type.headline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.my_qr_hint),
            color = Blesk.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Blesk.dimens.gutter * 2, vertical = 8.dp),
        )
    }
}
