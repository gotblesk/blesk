package app.blesk.feature.chats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.glint
import app.blesk.core.design.haptics.rememberHaptics
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.lerp
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import java.time.Instant

private val ChannelGap = 10.dp
private val ClusterTail = 16.dp
private val DropHeight = 22.dp
private val DropWidth = 40.dp
private const val AVATAR_INSET = 1.9f

/**
 * A room in the list: its channels hang under it as a cluster of drops stuck together. A tap
 * splits the cluster into separate channel capsules right in the list, no server column.
 * Small dots on the voice drop show who is talking.
 */
@Composable
internal fun RoomCluster(
    room: Conversation,
    initiallyOpen: Boolean,
    now: Instant,
    onOpenChannel: (RoomChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(room.id) { mutableStateOf(initiallyOpen) }
    val haptics = rememberHaptics()
    val progress by animateFloatAsState(if (expanded) 1f else 0f, Blesk.motion.stretch(), label = "room")
    val geometry = remember { ClusterGeometry() }
    val colors = Blesk.colors
    val density = LocalDensity.current
    val shape = RoundedCornerShape(Blesk.dimens.capsuleRadius)
    val capsuleRadius = with(density) { Blesk.dimens.capsuleRadius.toPx() }
    val stateLabel = stringResource(if (expanded) R.string.chats_room_expanded else R.string.chats_room_collapsed)
    val speakingLabel = stringResource(R.string.chats_speaking)
    val speakingNames = room.channels.flatMap { channel -> channel.participants.filter { it.id in channel.speaking } }
        .joinToString { it.name }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .metaballs(color = colors.surface, smoothness = 14.dp) {
                geometry.blobs(progress, capsuleRadius, DropWidth.toPx(), DropHeight.toPx())
            }
            .drawWithContent {
                drawContent()
                if (progress < 1f) drawSpeakingDots(room, geometry, 1f - progress, colors.light, colors.textTertiary)
            }
            .semantics { if (speakingNames.isNotEmpty()) contentDescription = "$speakingNames, $speakingLabel" },
        content = {
            Box(
                Modifier
                    .clip(shape)
                    .glint(shape, enabled = room.protection == Protection.EndToEnd)
                    .clickable(role = Role.Button) {
                        expanded = !expanded
                        if (expanded) haptics.detach() else haptics.merge()
                    }
                    .semantics { stateDescription = stateLabel },
            ) {
                ConversationSummary(room, typing = false, now = now, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
            }
            room.channels.forEach { channel ->
                ChannelRow(
                    channel = channel,
                    onClick = { onOpenChannel(channel) },
                    modifier = Modifier
                        .graphicsLayer { alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f) }
                        .let { if (expanded) it else it.clearAndSetSemantics { } },
                )
            }
        },
    ) { measurables, constraints ->
        val loose = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth)
        val header = measurables.first().measure(loose)
        val rows = measurables.drop(1).map { it.measure(loose) }
        val gap = ChannelGap.roundToPx()
        val tail = ClusterTail.roundToPx()
        val expandedExtra = rows.sumOf { it.height + gap }
        val height = header.height + lerp(tail.toFloat(), expandedExtra.toFloat(), progress).toInt()
        geometry.width = constraints.maxWidth.toFloat()
        geometry.header = header.height.toFloat()
        geometry.rows = rows.map { it.height.toFloat() }
        geometry.gap = gap.toFloat()
        layout(constraints.maxWidth, height) {
            header.place(0, 0)
            var y = header.height + gap
            rows.forEach { row ->
                row.place(0, y)
                y += row.height + gap
            }
        }
    }
}

/** Where the drops are, written during layout and read while drawing. */
private class ClusterGeometry {
    var width = 0f
    var header = 0f
    var rows: List<Float> = emptyList()
    var gap = 0f

    fun collapsedDrop(index: Int, dropWidth: Float, dropHeight: Float): Rect {
        val left = dropWidth * AVATAR_INSET + index * (dropWidth * 0.82f)
        val top = header - dropHeight * 0.3f
        return Rect(left, top, left + dropWidth, top + dropHeight)
    }

    fun blobs(progress: Float, radius: Float, dropWidth: Float, dropHeight: Float): List<Blob> {
        val head = Blob(Rect(0f, 0f, width, header), radius)
        var y = header + gap
        val drops = rows.mapIndexed { index, rowHeight ->
            val collapsed = Blob(collapsedDrop(index, dropWidth, dropHeight), dropHeight / 2f)
            val open = Blob(Rect(0f, y, width, y + rowHeight), radius)
            y += rowHeight + gap
            lerp(collapsed, open, progress)
        }
        return listOf(head) + drops
    }
}

/** Small dots on the voice channel's drop, one per person; the ones talking are lit. */
private fun DrawScope.drawSpeakingDots(room: Conversation, geometry: ClusterGeometry, alpha: Float, lit: Color, idle: Color) {
    val index = room.channels.indexOfFirst { it.kind == RoomChannelKind.Voice && it.participants.isNotEmpty() }
    if (index < 0) return
    val channel = room.channels[index]
    val drop = geometry.collapsedDrop(index, DropWidth.toPx(), DropHeight.toPx())
    val r = 2.5.dp.toPx()
    val step = 7.dp.toPx()
    val people = channel.participants.take(3)
    val startX = drop.center.x - step * (people.size - 1) / 2f
    people.forEachIndexed { i, person ->
        val color = if (person.id in channel.speaking) lit else idle
        drawCircle(color.copy(alpha = color.alpha * alpha), r, Offset(startX + i * step, drop.center.y))
    }
}

@Composable
private fun ChannelRow(channel: RoomChannel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = when (channel.kind) {
        RoomChannelKind.Text -> stringResource(R.string.chats_text_channel, channel.title)
        RoomChannelKind.Voice -> stringResource(R.string.chats_voice_channel, channel.title)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon = if (channel.kind == RoomChannelKind.Voice) BleskIcons.Voice else BleskIcons.Hash,
            contentDescription = null,
            tint = Blesk.colors.textSecondary,
            size = 20.dp,
        )
        Text(channel.title, style = Blesk.type.bodyStrong, maxLines = 1, modifier = Modifier.weight(1f))
        if (channel.kind == RoomChannelKind.Voice) {
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                channel.participants.forEach { Avatar(it.name, size = 26.dp) }
            }
        }
        if (channel.unread > 0) UnreadBadge(channel.unread)
    }
}
