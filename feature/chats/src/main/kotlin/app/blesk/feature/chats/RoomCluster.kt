package app.blesk.feature.chats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.lerp
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import java.time.Instant

private val ChannelGap = 10.dp
private val DropHeight = 30.dp
private val DropWidth = 56.dp

/** Room for the closed channel capsules hanging under the room. */
private val ClusterTail = DropHeight + 14.dp
private const val AVATAR_INSET = 1.4f

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
    /** The room's menu; null where the press is taken by dragging instead. */
    onLongPress: (() -> Unit)? = null,
    /** «+ Канал» after the channels, for those who may make one. */
    onAddChannel: (() -> Unit)? = null,
    /** A channel's menu: «Изменить», «Права», «Удалить». */
    onChannelMenu: ((RoomChannel) -> Unit)? = null,
) {
    var expanded by rememberSaveable(room.id) { mutableStateOf(initiallyOpen) }
    val haptics = rememberHaptics()
    val progress by animateFloatAsState(if (expanded) 1f else 0f, Blesk.motion.stretch(), label = "room")
    val geometry = remember { ClusterGeometry() }
    val hash = rememberVectorPainter(BleskIcons.Hash)
    val voice = rememberVectorPainter(BleskIcons.Voice)
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
                if (progress < 1f) {
                    drawClosedChannels(room, geometry, 1f - progress, ClosedInk(hash, voice, colors.textSecondary, colors.accent))
                    drawSpeakingDots(room, geometry, 1f - progress, colors.light, colors.textTertiary)
                }
            }
            .semantics { if (speakingNames.isNotEmpty()) contentDescription = "$speakingNames, $speakingLabel" },
        content = {
            Box(
                Modifier
                    .clip(shape)
                    .combinedClickable(
                        role = Role.Button,
                        onLongClick = onLongPress,
                        onClick = {
                            expanded = !expanded
                            if (expanded) haptics.detach() else haptics.stick()
                        },
                    )
                    .semantics { stateDescription = stateLabel },
            ) {
                ConversationSummary(room, typing = false, now = now, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
            }
            val rowsShown = Modifier
                .graphicsLayer { alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f) }
                .let { if (expanded) it else it.clearAndSetSemantics { } }
            room.channels.forEach { channel ->
                ChannelRow(
                    channel = channel,
                    onClick = { onOpenChannel(channel) },
                    onLongClick = onChannelMenu?.let { menu -> { menu(channel) } },
                    modifier = rowsShown,
                )
            }
            if (onAddChannel != null) AddChannelRow(onAddChannel, rowsShown)
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

    /** How many closed capsules fit in a row; the rest wait in the last one. */
    var fits = Int.MAX_VALUE

    fun collapsedDrop(index: Int, dropWidth: Float, dropHeight: Float, gap: Float = dropWidth * GAP_SHARE, hang: Float = 0f): Rect {
        val slot = index.coerceAtMost(fits - 1)
        val left = dropWidth * AVATAR_INSET + slot * (dropWidth + gap)
        val top = header + hang
        return Rect(left, top, left + dropWidth, top + dropHeight)
    }

    fun blobs(progress: Float, radius: Float, dropWidth: Float, dropHeight: Float): List<Blob> {
        val head = Blob(Rect(0f, 0f, width, header), radius)
        var y = header + gap
        fits = ((width - dropWidth * AVATAR_INSET) / (dropWidth * (1f + GAP_SHARE))).toInt().coerceAtLeast(1)
        val drops = rows.mapIndexed { index, rowHeight ->
            val closed = collapsedDrop(index, dropWidth, dropHeight, hang = dropHeight * HANG_SHARE)
            val collapsed = Blob(closed, dropHeight / 2f)
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
    val drop = geometry.collapsedDrop(index, DropWidth.toPx(), DropHeight.toPx(), hang = DropHeight.toPx() * HANG_SHARE)
    val r = 2.5.dp.toPx()
    val step = 7.dp.toPx()
    val people = channel.participants.take(3)
    val startX = drop.center.x - step * (people.size - 1) / 2f
    people.forEachIndexed { i, person ->
        val color = if (person.id in channel.speaking) lit else idle
        drawCircle(color.copy(alpha = color.alpha * alpha), r, Offset(startX + i * step, drop.center.y))
    }
}

/** What the closed capsules are drawn with. */
private class ClosedInk(val hash: Painter, val voice: Painter, val icon: Color, val unread: Color)

/**
 * Each closed channel capsule shows what it is: «#» for a text channel, a dot of light when it has
 * unread messages; a voice channel shows its people as dots (see [drawSpeakingDots]) or its icon.
 */
private fun DrawScope.drawClosedChannels(room: Conversation, geometry: ClusterGeometry, alpha: Float, ink: ClosedInk) {
    val iconSize = 16.dp.toPx()
    room.channels.forEachIndexed { index, channel ->
        if (index >= geometry.fits) return
        val drop = geometry.collapsedDrop(index, DropWidth.toPx(), DropHeight.toPx(), hang = DropHeight.toPx() * HANG_SHARE)
        val talking = channel.kind == RoomChannelKind.Voice && channel.participants.isNotEmpty()
        if (talking) return@forEachIndexed
        val painter = if (channel.kind == RoomChannelKind.Voice) ink.voice else ink.hash
        translate(drop.center.x - iconSize / 2f, drop.center.y - iconSize / 2f) {
            with(painter) { draw(Size(iconSize, iconSize), alpha = alpha, colorFilter = ColorFilter.tint(ink.icon)) }
        }
        if (channel.unread > 0) {
            drawCircle(ink.unread.copy(alpha = alpha), 3.dp.toPx(), Offset(drop.right - 9.dp.toPx(), drop.top + 8.dp.toPx()))
        }
    }
}

/** «+ Канал»: one more drop of the cluster, for those who may make a channel. */
@Composable
private fun AddChannelRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon = BleskIcons.Plus, contentDescription = null, tint = Blesk.colors.textSecondary, size = 20.dp)
        Text(stringResource(R.string.channel_add_capsule), style = Blesk.type.bodyStrong, color = Blesk.colors.textSecondary)
    }
}

@Composable
private fun ChannelRow(channel: RoomChannel, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
    val haptics = rememberHaptics()
    val description = when (channel.kind) {
        RoomChannelKind.Text -> stringResource(R.string.chats_text_channel, channel.title)
        RoomChannelKind.Voice -> stringResource(R.string.chats_voice_channel, channel.title)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClickLabel = onLongClick?.let { stringResource(R.string.channel_menu_edit) },
                onLongClick = onLongClick?.let {
                    {
                        haptics.menu()
                        it()
                    }
                },
            )
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

/** Between two closed capsules: narrow enough for the metaball to pull a neck across. */
private const val GAP_SHARE = 0.16f

/** The closed capsules hang this far under the room, by their height, so a neck joins them to it. */
private const val HANG_SHARE = 0.18f
