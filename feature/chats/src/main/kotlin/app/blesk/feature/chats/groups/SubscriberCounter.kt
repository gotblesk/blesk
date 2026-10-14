package app.blesk.feature.chats.groups

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Text
import app.blesk.core.design.fire.dithered
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.MenuSheet
import app.blesk.feature.chats.R
import app.blesk.feature.chats.groupedCount
import kotlinx.coroutines.launch

/** The round numbers a channel celebrates. */
private val MILESTONES = listOf(100, 1_000, 10_000, 100_000, 1_000_000)

internal fun milestoneOf(count: Int): Int? = MILESTONES.lastOrNull { count >= it }

/**
 * «● 1 248 подписчиков» under a channel's name: a dot of light and the number in even-width
 * digits, each rolling to its new value. For an admin a newcomer arrives as a small dot that flows
 * into the capsule; reaching a round number runs fire over it and calls [onMilestone].
 */
@Composable
internal fun SubscriberCapsule(count: Int, members: Boolean, admin: Boolean, onMilestone: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val arrive = remember { Animatable(1f) }
    var shown by remember { mutableIntStateOf(count) }
    val milestone = milestoneOf(count)
    LaunchedEffect(count) {
        val grew = count > shown
        shown = count
        if (grew && admin) {
            arrive.snapTo(0f)
            arrive.animateTo(1f, tween(ARRIVE_MILLIS))
            haptics.tick()
        }
    }
    var celebrated by remember { mutableIntStateOf(milestone ?: 0) }
    LaunchedEffect(milestone) {
        val reached = milestone ?: return@LaunchedEffect
        if (reached > celebrated) {
            celebrated = reached
            if (admin) {
                haptics.success()
                onMilestone(reached)
            }
        }
    }
    val words = if (members) R.plurals.count_members_word else R.plurals.count_subscribers_word
    val word = pluralStringResource(words, count)
    val spoken = "${groupedCount(count)} $word"
    val light = Blesk.colors.light
    val digits = Blesk.type.bodyStrong.copy(fontFeatureSettings = "tnum")
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Blesk.colors.surface)
            .flashOn(milestone, CircleShape)
            .drawBehind {
                val t = arrive.value
                if (t >= 1f) return@drawBehind
                // The newcomer: a dot coming in from the right edge, fading as it joins the light.
                val start = Offset(size.width + ARRIVE_FROM.toPx(), size.height / 2f)
                val end = Offset(LIGHT_X.toPx(), size.height / 2f)
                drawCircle(light.copy(alpha = 1f - t * t), radius = 3.dp.toPx(), center = start + (end - start) * t)
            }
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(light, CircleShape))
        Row {
            val text = groupedCount(count)
            text.forEachIndexed { index, char ->
                // Keyed from the right, so a digit keeps its place while the number grows on the left.
                key(text.length - index) {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) using
                                SizeTransform(clip = true)
                        },
                        label = "digit",
                    ) { Text(it.toString(), style = digits) }
                }
            }
        }
        Text(word, style = Blesk.type.body, color = Blesk.colors.textSecondary)
    }
}

/**
 * «Каналу 1 000 подписчиков»: a card drawn in code in the light of the theme, in the style of the
 * invite card; it can be kept as a picture.
 */
@Composable
internal fun MilestoneCard(title: String, milestone: Int, onSave: (suspend (ImageBitmap) -> Unit)?, onDismiss: () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val light = Blesk.colors.light
    val ground = Blesk.colors.background
    val words = pluralStringResource(R.plurals.count_subscribers_word, milestone)
    MenuSheet(title = title, onDismiss = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_RATIO)
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(ground)
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                listOf(light.copy(alpha = GLOW), Color.Transparent),
                                center = Offset(size.width * 0.7f, size.height * 0.25f),
                                radius = size.maxDimension * 0.8f,
                            ),
                        )
                    }
                    .dithered(),
            )
            Column(
                Modifier.matchParentSize().padding(28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.milestone_brand), style = Blesk.type.label, color = Blesk.colors.textSecondary)
                Column {
                    Text(
                        groupedCount(milestone),
                        style = Blesk.type.title.copy(fontSize = 56.sp, lineHeight = 60.sp, fontFeatureSettings = "tnum"),
                    )
                    Text(
                        stringResource(R.string.milestone_title, "${groupedCount(milestone)} $words"),
                        style = Blesk.type.bodyStrong,
                        textAlign = TextAlign.Start,
                    )
                }
                Text(title, style = Blesk.type.body, color = Blesk.colors.textSecondary, maxLines = 1)
            }
        }
        if (onSave != null) {
            Button(
                text = stringResource(R.string.milestone_save),
                icon = BleskIcons.Download,
                kind = ButtonKind.Secondary,
                onClick = { scope.launch { onSave(layer.toImageBitmap()) } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

private val ARRIVE_FROM = 24.dp
private val LIGHT_X = 18.dp
private const val ARRIVE_MILLIS = 700
private const val GLOW = 0.55f
private const val CARD_RATIO = 0.8f
