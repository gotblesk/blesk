package app.blesk.feature.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One found message: where it is and a piece of it. */
data class SearchHit(val conversationId: String, val messageId: String, val place: String, val author: String, val text: String)

/**
 * Results of the search unfolded from the top lens of «Чаты»: chats, people and messages.
 * Messages are searched on this phone only; nothing is sent anywhere.
 */
@Composable
fun SearchResults(
    query: String,
    hits: ImmutableList<SearchHit>?,
    onOpen: (SearchHit) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    chats: ImmutableList<Conversation> = persistentListOf(),
    people: ImmutableList<Contact> = persistentListOf(),
    onOpenChat: (String) -> Unit = {},
    onOpenPerson: (Contact) -> Unit = {},
    /** Looks the typed username up on the server; null in the demo. */
    onFindUsername: ((String) -> Unit)? = null,
) {
    val needle = query.trim()
    val matchLight = Blesk.colors.light.copy(alpha = if (Blesk.colors.isDark) MATCH_DARK else MATCH_LIGHT)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        if (needle.isEmpty()) {
            Text(
                text = stringResource(R.string.search_hint),
                color = Blesk.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 8.dp),
            )
        }
        if (chats.isNotEmpty()) {
            SectionHeader(stringResource(R.string.search_chats))
            chats.forEach { chat ->
                ResultRow(chat.title, null, chat.id, onClick = { onOpenChat(chat.id) })
            }
        }
        val username = needle.removePrefix("@").lowercase()
        val looksLikeUsername = username.length >= MIN_USERNAME && username.all { it.isLetterOrDigit() || it == '_' || it == '.' }
        val canLookUp = onFindUsername != null && looksLikeUsername
        if (people.isNotEmpty() || canLookUp) {
            SectionHeader(stringResource(R.string.search_people))
            people.forEach { person -> ResultRow(person.name, "@${person.username}", person.id, onClick = { onOpenPerson(person) }) }
            if (canLookUp && people.none { it.username == username }) {
                ResultRow(
                    title = stringResource(R.string.search_find_username, username),
                    subtitle = null,
                    key = username,
                    onClick = { onFindUsername?.invoke(username) },
                )
            }
        }
        if (!hits.isNullOrEmpty()) SectionHeader(stringResource(R.string.search_messages))
        Column(Modifier.padding(top = 4.dp)) {
            val nothing = chats.isEmpty() && people.isEmpty() && !canLookUp
            if (nothing && hits?.isEmpty() == true && needle.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.search_empty),
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            hits?.forEach { hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpen(hit) }
                        .padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp),
                ) {
                    Text(hit.place, style = Blesk.type.bodyStrong, maxLines = 1)
                    Text(
                        text = highlight("${hit.author}: ${snippet(hit.text, needle)}", needle, matchLight),
                        style = Blesk.type.label,
                        color = Blesk.colors.textSecondary,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String?, key: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(title, key = key, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = Blesk.type.bodyStrong, maxLines = 1)
            subtitle?.let { Text(it, style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1) }
        }
    }
}

private const val MIN_USERNAME = 3
private const val MATCH_LIGHT = 0.6f
private const val MATCH_DARK = 0.3f

/** Keeps the match in view for long messages. */
private fun snippet(text: String, needle: String): String {
    val at = text.indexOf(needle, ignoreCase = true)
    if (at < SNIPPET_LEAD || needle.isEmpty()) return text
    return "…" + text.substring(at - SNIPPET_LEAD)
}

/** Matches are marked with the theme's light, so they read as lit rather than bolded. */
private fun highlight(text: String, needle: String, light: Color): AnnotatedString = buildAnnotatedString {
    var from = 0
    while (needle.isNotEmpty()) {
        val at = text.indexOf(needle, from, ignoreCase = true)
        if (at < 0) break
        append(text.substring(from, at))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, background = light)) { append(text.substring(at, at + needle.length)) }
        from = at + needle.length
    }
    append(text.substring(from))
}

private const val SNIPPET_LEAD = 24
