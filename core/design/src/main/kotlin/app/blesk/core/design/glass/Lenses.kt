package app.blesk.core.design.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.blesk.core.design.R
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.fire.lightSweep
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.icon.LivingIcon
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.condense
import app.blesk.core.motion.rememberHaptics
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val LensShape = RoundedCornerShape(30.dp)

/**
 * The top lens: a glass capsule under the status bar holding the title and actions. With
 * [collapsed] set, the title starts large right on the background and gathers into the lens
 * once the content scrolls under it.
 */
@Composable
fun TopLens(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    leading: @Composable RowScope.() -> Unit = {},
    collapsed: Boolean? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val large = collapsed == false
    val glass by animateFloatAsState(if (large) 0f else 1f, label = "lens")
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (glass > 0f) {
            Box(Modifier.fillMaxWidth().height(60.dp).graphicsLayer { alpha = glass }.lens(LensShape))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onBack != null) {
                IconButton(BleskIcons.Back, stringResource(R.string.design_back), onBack)
            } else {
                Spacer(Modifier.width(10.dp))
            }
            leading()
            if (title != null) {
                Text(
                    text = title,
                    style = when {
                        // A collapsible title shrinks into the lens and grows denser on the way.
                        collapsed != null -> condense(lerp(Blesk.type.display, Blesk.type.title, glass), glass)
                        onBack == null -> Blesk.type.title
                        else -> Blesk.type.headline
                    },
                    maxLines = 1,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            actions()
        }
    }
}

/** A tab of the bottom lens; [badge] replaces the icon, for the profile tab's avatar. */
@Immutable
data class LensTab(
    val label: String,
    val icon: ImageVector,
    /** How the icon comes alive when chosen; null keeps it still. */
    val living: LivingIcon? = null,
    val badge: (@Composable () -> Unit)? = null,
)

/** The main action of a tab, shown as a round capsule stuck to the right of the navigation lens. */
@Immutable
data class LensAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * The bottom lens as navigation. The selection is a drop of light: on a switch it stretches
 * from the old tab to the new one, then comes away on the spot.
 */
@Composable
fun LensTabBar(
    tabs: ImmutableList<LensTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    action: LensAction? = null,
) {
    val haptics = rememberHaptics()
    val motion = Blesk.motion
    val lead = remember { Animatable(selected.toFloat()) }
    val tail = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) {
        if (motion.reduced) {
            lead.snapTo(selected.toFloat())
            tail.snapTo(selected.toFloat())
            return@LaunchedEffect
        }
        launch { lead.animateTo(selected.toFloat(), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) }
        launch { tail.animateTo(selected.toFloat(), spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)) }
    }
    val drop = Blesk.colors.light.copy(alpha = if (Blesk.colors.isDark) 0.16f else 0.55f)
    // The selected tab widens to hold its label; the rest stay icons no narrower than a touch target,
    // so five tabs fit a narrow phone. Where even that leaves no room, the label gives way, not the target.
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = Blesk.type.caption
    var barWidth by remember { mutableIntStateOf(0) }
    val label = tabs.getOrNull(selected)?.label.orEmpty()
    val needed = with(density) { measurer.measure(label, labelStyle, maxLines = 1).size.width.toDp() } + LabelChrome
    val inner = with(density) { barWidth.toDp() } - BarPadding * 2
    val other = maxOf(Blesk.dimens.touchTarget, (inner - needed) / (tabs.size - 1).coerceAtLeast(1))
    val wide = inner - other * (tabs.size - 1)
    val showLabel = barWidth > 0 && wide >= needed - 1.dp
    val weights = tabs.indices.map { index ->
        val target = if (index == selected && barWidth > 0) (wide / other).coerceAtLeast(1f) else 1f
        animateFloatAsState(target, motion.settle(), label = "tabWeight").value
    }
    // Keeps the last action while the capsule draws back into the lens, so it can leave in its own shape.
    var shown by remember { mutableStateOf(action) }
    if (action != null) shown = action
    val spring by animateFloatAsState(if (action != null) 1f else 0f, motion.settle(), label = "action") {
        if (it == 0f) shown = null
    }
    // The settle spring overshoots; the capsule may swell past full, but the bar never grows past its edge.
    val reach = spring.coerceIn(0f, 1f)
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = (BarHeight + ActionGap) * reach)
                .height(BarHeight)
                .onSizeChanged { barWidth = it.width }
                .lens(CircleShape)
                .padding(BarPadding)
                .metaballs(color = drop, smoothness = DropSmoothness) {
                    // The smoothing swells the drop by about a quarter of itself; the drop is cut down by
                    // as much, so its visible edge keeps the bar's padding from the lens and is never clipped.
                    tabDrops(lead.value, tail.value, weights, size.width, size.height, inset = DropSmoothness.toPx() / 4f)
                }
                .selectableGroup(),
        ) {
            tabs.forEachIndexed { index, tab ->
                val active = index == selected
                Row(
                    modifier = Modifier
                        .weight(weights[index])
                        .fillMaxHeight()
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!active) {
                                haptics.tick()
                                onSelect(index)
                            }
                        }
                        .semantics { contentDescription = tab.label },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (active) Blesk.colors.textPrimary else Blesk.colors.textSecondary
                    val badge = tab.badge
                    val alive by animateFloatAsState(if (active) 1f else 0f, motion.stretch(), label = "alive")
                    val living = tab.living
                    when {
                        // The avatar of «Ты» catches a glint as its tab is chosen.
                        badge != null -> Box(Modifier.lightSweep(active, CircleShape)) { badge() }
                        living != null -> LivingIcon(living, { alive }, tint, Blesk.colors.light)
                        else -> Icon(tab.icon, contentDescription = null, tint = tint, size = 24.dp)
                    }
                    AnimatedVisibility(
                        visible = active && showLabel,
                        enter = fadeIn(motion.settle()) + expandHorizontally(motion.settle()),
                        exit = fadeOut(motion.press()) + shrinkHorizontally(motion.settle()),
                    ) {
                        Text(
                            text = tab.label,
                            style = labelStyle,
                            color = tint,
                            maxLines = 1,
                            modifier = Modifier.padding(start = LabelGap).clearAndSetSemantics { },
                        )
                    }
                }
            }
        }
        shown?.let { current ->
            ActionCapsule(
                action = current,
                enabled = action != null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer {
                        // Fading without an offscreen layer: such a layer is a rectangle the size of the capsule
                        // and would cut its shadow into a square. The shadow fades and goes with the capsule.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        shape = CircleShape
                        // Drawn back towards the lens: the capsule shrinks and slides into the bar's end.
                        val scale = SHRUNK + (1f - SHRUNK) * reach
                        scaleX = scale
                        scaleY = scale
                        alpha = reach
                        translationX = -(1f - reach) * (BarHeight + ActionGap).toPx() / 2f
                    },
            )
        }
        if (shown != null && !motion.reduced && reach > BRIDGE_FROM) {
            // The neck lies over both lenses' ends and covers the arcs of their rims where they face each
            // other, so the three read as one liquid; it has no rim or shadow of its own to leave a seam.
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = BarHeight / 2)
                    .width(BarHeight + ActionGap)
                    .height(BarHeight)
                    .graphicsLayer {
                        alpha = (reach - BRIDGE_FROM) / (1f - BRIDGE_FROM)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .lens(BridgeShape, elevation = 0.dp, refract = false, rim = false),
            )
        }
    }
}

/** The icon flows into the next one when the tab changes: the old one shrinks away as the new one swells. */
@Composable
private fun ActionCapsule(action: LensAction, enabled: Boolean, modifier: Modifier = Modifier) {
    val motion = Blesk.motion
    Box(
        modifier = modifier
            .size(BarHeight)
            .lens(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = action.label, onClick = action.onClick)
            .semantics { contentDescription = action.label },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = action.icon,
            transitionSpec = {
                (scaleIn(motion.settle(), initialScale = 0.4f) + fadeIn(motion.press()))
                    .togetherWith(scaleOut(motion.press(), targetScale = 0.4f) + fadeOut(motion.press()))
            },
            label = "action",
        ) { icon ->
            Icon(icon, contentDescription = null, tint = Blesk.colors.textPrimary, size = 24.dp)
        }
    }
}

private val BarHeight = 64.dp
private val ActionGap = 8.dp
private val BarPadding = 6.dp

/** Icon, gap and breathing room around the selected tab's label. */
private val LabelGap = 4.dp
private val LabelChrome = 24.dp + LabelGap + 12.dp
private const val SHRUNK = 0.4f

/** The bridge only shows once the capsule is mostly out, so it never hangs off a shrinking capsule. */
private const val BRIDGE_FROM = 0.7f

/**
 * The neck between the end of the navigation lens and the action capsule, in the middle of their
 * height. Each edge leaves one circle along its tangent and enters the other along its tangent, so
 * the glass runs on without a lip above or below; the circles themselves are cut out so no glass
 * is drawn twice. The box spans from the centre of the bar's rounded end to the centre of the capsule.
 */
private val BridgeShape = GenericShape { size, _ ->
    val radius = size.height / 2f
    val cy = size.height / 2f
    val dx = radius * cos(BRIDGE_ANGLE)
    val dy = radius * sin(BRIDGE_ANGLE)
    // Along the circles' tangents at the meeting points, far enough to dip to a narrow waist.
    val tx = sin(BRIDGE_ANGLE) * BRIDGE_PULL * (size.width / 2f - dx)
    val ty = cos(BRIDGE_ANGLE) * BRIDGE_PULL * (size.width / 2f - dx)
    val neck = Path().apply {
        moveTo(dx, cy - dy)
        cubicTo(dx + tx, cy - dy + ty, size.width - dx - tx, cy - dy + ty, size.width - dx, cy - dy)
        lineTo(size.width - dx, cy + dy)
        cubicTo(size.width - dx - tx, cy + dy - ty, dx + tx, cy + dy - ty, dx, cy + dy)
        close()
    }
    addPath(neck)
}

private const val BRIDGE_ANGLE = 0.55f
private const val BRIDGE_PULL = 1.6f
private val DropSmoothness = 18.dp

private fun tabDrops(lead: Float, tail: Float, weights: List<Float>, width: Float, height: Float, inset: Float): List<Blob> {
    if (weights.isEmpty()) return emptyList()
    val total = weights.sum()
    val starts = weights.runningFold(0f) { acc, w -> acc + w }.map { it / total * width }

    // A position between two tabs blends their slots, so the drop widens and narrows as it travels.
    fun slot(position: Float): Pair<Float, Float> {
        val clamped = position.coerceIn(0f, weights.lastIndex.toFloat())
        val from = clamped.toInt()
        val to = minOf(from + 1, weights.lastIndex)
        val t = clamped - from
        fun centre(i: Int) = (starts[i] + starts[i + 1]) / 2f
        fun span(i: Int) = starts[i + 1] - starts[i]
        return (centre(from) + (centre(to) - centre(from)) * t) to (span(from) + (span(to) - span(from)) * t)
    }
    fun drop(position: Float, scale: Float): Blob {
        val (cx, span) = slot(position)
        val h = (height - inset * 2f) * (0.55f + 0.45f * scale)
        val w = ((span - inset * 2f) * scale).coerceAtLeast(h)
        val cy = height / 2f
        val left = (cx - w / 2f).coerceIn(inset, width - inset - w)
        return Blob(Rect(left, cy - h / 2f, left + w, cy + h / 2f), h / 2f)
    }
    val stretch = abs(lead - tail).coerceAtMost(1f)
    return listOf(drop(lead, 1f - 0.15f * stretch), drop(tail, 0.75f - 0.25f * stretch))
}

/** Bottom lens holding arbitrary content, such as the message composer. Rises with the keyboard. */
@Composable
fun BottomLens(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().lens(LensShape).padding(horizontal = 6.dp, vertical = 4.dp),
            content = content,
        )
    }
}
