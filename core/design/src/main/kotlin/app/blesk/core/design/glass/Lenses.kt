package app.blesk.core.design.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.R
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
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
                        large -> Blesk.type.display
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
data class LensTab(val label: String, val icon: ImageVector, val badge: (@Composable () -> Unit)? = null)

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
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        if (action != null && !motion.reduced) {
            // The bridge sits under both lenses' edges, so it reads as one liquid with them.
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = BarHeight / 2)
                    .width(BarHeight + ActionGap)
                    .height(BarHeight)
                    .lens(BridgeShape, elevation = 0.dp, refract = false),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = if (action != null) BarHeight + ActionGap else 0.dp)
                .height(BarHeight)
                .lens(CircleShape)
                .padding(6.dp)
                .metaballs(color = drop, smoothness = 18.dp) {
                    tabDrops(lead.value, tail.value, tabs.size, size.width, size.height)
                }
                .selectableGroup(),
        ) {
            tabs.forEachIndexed { index, tab ->
                val active = index == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
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
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val tint = if (active) Blesk.colors.textPrimary else Blesk.colors.textSecondary
                    val badge = tab.badge
                    if (badge != null) badge() else Icon(tab.icon, contentDescription = null, tint = tint, size = 22.dp)
                    Text(tab.label, style = Blesk.type.caption, color = tint, maxLines = 1)
                }
            }
        }
        if (action != null) ActionCapsule(action, Modifier.align(Alignment.CenterEnd))
    }
}

/** The icon flows into the next one when the tab changes: the old one shrinks away as the new one swells. */
@Composable
private fun ActionCapsule(action: LensAction, modifier: Modifier = Modifier) {
    val motion = Blesk.motion
    Box(
        modifier = modifier
            .size(BarHeight)
            .lens(CircleShape)
            .clickable(role = Role.Button, onClickLabel = action.label, onClick = action.onClick)
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
private val ActionGap = 10.dp

/**
 * The neck between the end of the navigation lens and the action capsule: two concave curves
 * between the circles, with the circles themselves cut out so no glass is drawn twice.
 * The box spans from the centre of the bar's rounded end to the centre of the capsule.
 */
private val BridgeShape = GenericShape { size, _ ->
    val radius = size.height / 2f
    val cy = size.height / 2f
    val waist = size.height * BRIDGE_WAIST
    val dx = radius * cos(BRIDGE_ANGLE)
    val dy = radius * sin(BRIDGE_ANGLE)
    val neck = Path().apply {
        moveTo(dx, cy - dy)
        quadraticTo(size.width / 2f, cy - waist, size.width - dx, cy - dy)
        lineTo(size.width - dx, cy + dy)
        quadraticTo(size.width / 2f, cy + waist, dx, cy + dy)
        close()
    }
    val circles = Path().apply {
        addOval(Rect(Offset(0f, cy), radius))
        addOval(Rect(Offset(size.width, cy), radius))
    }
    addPath(Path.combine(PathOperation.Difference, neck, circles))
}

private const val BRIDGE_WAIST = 0.16f
private const val BRIDGE_ANGLE = 0.75f

private fun tabDrops(lead: Float, tail: Float, count: Int, width: Float, height: Float): List<Blob> {
    if (count == 0) return emptyList()
    val slot = width / count
    fun drop(position: Float, scale: Float): Blob {
        val w = slot * scale
        val h = height * (0.55f + 0.45f * scale)
        val cx = slot * (position + 0.5f)
        val cy = height / 2f
        return Blob(Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), h / 2f)
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
