package app.blesk.feature.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.Connection
import app.blesk.core.design.component.Text
import app.blesk.core.design.glass.lens
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.delay

/**
 * The link status, coming away from the top lens as a small capsule of the same glass. A state has to last
 * longer than a second to be shown, so a short reconnect never flickers; when everything is
 * fine again the capsule goes back up into the lens.
 */
@Composable
fun ConnectionCapsule(
    connection: Connection,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showAfterMillis: Long = SHOW_AFTER_MILLIS,
) {
    var shown by remember { mutableStateOf(Connection.Online) }
    LaunchedEffect(connection) {
        if (connection != Connection.Online) delay(showAfterMillis)
        shown = connection
    }
    val motion = Blesk.motion
    var last by remember { mutableStateOf(Connection.Connecting) }
    if (shown != Connection.Online) last = shown
    AnimatedVisibility(
        visible = shown != Connection.Online,
        modifier = modifier,
        enter = slideInVertically(motion.settle()) { -it } + fadeIn(motion.transition()),
        exit = slideOutVertically(motion.transition()) { -it } + shrinkVertically(motion.transition()) + fadeOut(motion.transition()),
    ) {
        val retry = last == Connection.Unreachable
        val retryLabel = stringResource(R.string.connection_retry)
        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .lens(CircleShape, elevation = 10.dp)
                .let { if (retry) it.clickable(role = Role.Button, onClickLabel = retryLabel, onClick = onRetry) else it }
                .heightIn(min = if (retry) 48.dp else 32.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(label(last)), style = Blesk.type.label, color = Blesk.colors.textPrimary)
            if (retry) Text(retryLabel, style = Blesk.type.bodyStrong, color = Blesk.colors.textPrimary)
        }
    }
}

/**
 * The capsule hung under the top lens without taking any height, so the content below never
 * jumps when it appears.
 */
@Composable
fun ConnectionStatus(
    connection: Connection,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showAfterMillis: Long = SHOW_AFTER_MILLIS,
) {
    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                layout(placeable.width, 0) { placeable.place(0, 0) }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        ConnectionCapsule(connection, onRetry, showAfterMillis = showAfterMillis)
    }
}

private fun label(connection: Connection): Int = when (connection) {
    Connection.NoNetwork -> R.string.connection_no_network
    Connection.Connecting, Connection.Online -> R.string.connection_connecting
    Connection.Updating -> R.string.connection_updating
    Connection.Unreachable -> R.string.connection_unreachable
}

private const val SHOW_AFTER_MILLIS = 1_000L
