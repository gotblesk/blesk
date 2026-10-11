package app.blesk.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.R
import app.blesk.core.design.theme.Blesk

/** A small matte label. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Blesk.type.caption,
        color = Blesk.colors.textSecondary,
        maxLines = 1,
        modifier = modifier
            .background(Blesk.colors.surfaceStrong, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The one way to mark something that is not ready yet. A control wearing it does nothing
 * when tapped and says so to TalkBack.
 */
@Composable
fun SoonTag(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.design_soon),
        style = Blesk.type.caption,
        color = Blesk.colors.textTertiary,
        maxLines = 1,
        modifier = modifier
            .border(Blesk.dimens.hairline, Blesk.colors.hairline, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** The demo mode mark: matte, always visible while no server is configured. */
@Composable
fun DemoTag(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.design_demo),
        style = Blesk.type.overline,
        color = Blesk.colors.textSecondary,
        maxLines = 1,
        modifier = modifier
            .background(Blesk.colors.surfaceStrong, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Signature of everything that is not end-to-end encrypted. */
@Composable
fun NotEncryptedTag(modifier: Modifier = Modifier) {
    Tag(text = stringResource(R.string.design_not_encrypted), modifier = modifier)
}
