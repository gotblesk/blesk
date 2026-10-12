package app.blesk.feature.chats

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One emoji with its Russian name and search words from Unicode CLDR. */
internal data class EmojiEntry(val emoji: String, val name: String, val keywords: String) {
    fun matches(query: String): Boolean =
        name.startsWith(query) || name.contains(" $query") || keywords.startsWith(query) || keywords.contains(" $query")
}

internal enum class EmojiSection(val id: String, val title: Int) {
    Recent("recent", R.string.emoji_recent),
    Emotions("emotions", R.string.emoji_emotions),
    People("people", R.string.emoji_people),
    Nature("animals_nature", R.string.emoji_nature),
    Food("food_drink", R.string.emoji_food),
    Travel("travel_places", R.string.emoji_travel),
    Activity("activity", R.string.emoji_activity),
    Objects("objects", R.string.emoji_objects),
    Symbols("symbols", R.string.emoji_symbols),
    Flags("flags", R.string.emoji_flags),
}

/**
 * Reads the bundled list, dropping emoji this phone cannot draw: older Android versions lack
 * the newest ones, and an empty box is worse than a missing choice.
 */
internal fun loadEmoji(context: Context): Map<EmojiSection, List<EmojiEntry>> {
    val paint = Paint()
    val sections = LinkedHashMap<EmojiSection, MutableList<EmojiEntry>>()
    var current: MutableList<EmojiEntry>? = null
    context.assets.open(EMOJI_ASSET).bufferedReader().useLines { lines ->
        lines.forEach { line ->
            if (line.startsWith("#")) {
                val section = EmojiSection.entries.firstOrNull { it.id == line.removePrefix("#") }
                current = section?.let { sections.getOrPut(it) { ArrayList() } }
                return@forEach
            }
            val parts = line.split('\t')
            if (parts.size == 3 && paint.hasGlyph(parts[0])) {
                current?.add(EmojiEntry(parts[0], parts[1], parts[2]))
            }
        }
    }
    return sections
}

/** Emoji with search and recent picks, shown in place of the keyboard. */
@Composable
internal fun EmojiPanel(recent: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier, height: Dp = PANEL_HEIGHT) {
    val context = LocalContext.current
    val catalog by produceState<Map<EmojiSection, List<EmojiEntry>>>(emptyMap()) {
        value = withContext(Dispatchers.IO) { loadEmoji(context) }
    }
    val byEmoji = remember(catalog) { catalog.values.flatten().associateBy { it.emoji } }
    var section by rememberSaveable { mutableStateOf(if (recent.isEmpty()) EmojiSection.Emotions else EmojiSection.Recent) }
    val query = rememberTextFieldState()
    val needle = query.text.toString().trim().lowercase()
    val shown = when {
        needle.isNotEmpty() -> catalog.values.flatten().filter { it.matches(needle) }.take(MAX_FOUND)
        section == EmojiSection.Recent -> recent.map { byEmoji[it] ?: EmojiEntry(it, it, "") }
        else -> catalog[section].orEmpty()
    }
    val searchHint = stringResource(R.string.emoji_search)
    Column(modifier.fillMaxWidth().height(height)) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
                .background(Blesk.colors.surface)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(BleskIcons.Search, contentDescription = null, tint = Blesk.colors.textTertiary)
            Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                if (query.text.isEmpty()) Text(searchHint, color = Blesk.colors.textTertiary)
                BasicTextField(
                    state = query,
                    textStyle = Blesk.type.body.copy(color = Blesk.colors.textPrimary),
                    cursorBrush = SolidColor(Blesk.colors.accent),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchHint },
                )
            }
        }
        if (needle.isEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(EmojiSection.entries.filter { it != EmojiSection.Recent || recent.isNotEmpty() }) { item ->
                    SectionChip(stringResource(item.title), item == section) { section = item }
                }
            }
        }
        if (shown.isEmpty() && needle.isNotEmpty() && catalog.isNotEmpty()) {
            Text(
                text = stringResource(R.string.emoji_nothing),
                color = Blesk.colors.textSecondary,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CELL),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(shown, key = { it.emoji }) { entry ->
                Box(
                    modifier = Modifier
                        .size(CELL)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onPick(entry.emoji) }
                        .semantics { contentDescription = entry.name.ifEmpty { entry.emoji } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(entry.emoji, style = Blesk.type.body.copy(fontSize = 26.sp, lineHeight = 30.sp))
                }
            }
        }
    }
}

@Composable
private fun SectionChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = Blesk.type.label,
            color = if (selected) Blesk.colors.onAccent else Blesk.colors.textSecondary,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (selected) Blesk.colors.accent else Blesk.colors.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private const val EMOJI_ASSET = "emoji.tsv"
private const val MAX_FOUND = 120
private val CELL = 48.dp
private val PANEL_HEIGHT = 300.dp
