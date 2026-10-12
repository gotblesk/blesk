package app.blesk.core.data.demo

import android.content.res.Resources
import app.blesk.core.data.R
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.ME
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Preview
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.Reaction
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.shared.protocol.BotCommand
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import java.time.Duration
import java.time.Instant

/** The initial demo world, built from string resources so all text stays translatable. */
class DemoSeed(
    val contacts: List<Contact>,
    val conversations: List<Conversation>,
    val messages: List<Message>,
    val echoBotConversationId: String,
    val echoBotAuthorId: String,
    val directContactIds: Set<String>,
    val bot: DemoBot = DemoBot(),
) {
    companion object {
        const val ANYA = "anya"
        const val MISHA = "misha"
        const val GROUP = "group"
        const val ROOM = "room"
        const val CHANNEL = "channel"
        const val BOT = "bot"
        const val SAVED = "saved"
        const val ROOM_GENERAL = "room-general"
        const val ROOM_DESIGN = "room-design"
        const val ROOM_VOICE = "room-voice"

        fun from(resources: Resources, now: Instant): DemoSeed {
            val contacts = listOf(
                Contact(ANYA, resources.getString(R.string.demo_name_anya), "anya"),
                Contact(MISHA, resources.getString(R.string.demo_name_misha), "misha.k"),
                Contact("lera", resources.getString(R.string.demo_name_lera), "lera"),
                Contact("dima", resources.getString(R.string.demo_name_dima), "orlov"),
                Contact("katya", resources.getString(R.string.demo_name_katya), "katya"),
                Contact(BOT, resources.getString(R.string.demo_name_bot), "echo_bot"),
                Contact(CHANNEL, resources.getString(R.string.demo_title_channel), "blesk"),
            )
            val byId = contacts.associateBy { it.id }

            data class Thread(val id: String, val array: Int, val minutesAgo: Long)

            val threads = listOf(
                Thread(ANYA, R.array.demo_chat_anya, 4),
                Thread(GROUP, R.array.demo_chat_group, 26),
                Thread(ROOM_GENERAL, R.array.demo_chat_room, 70),
                Thread(MISHA, R.array.demo_chat_misha, 180),
                Thread(CHANNEL, R.array.demo_chat_channel, 60 * 26),
                Thread(BOT, R.array.demo_chat_bot, 60 * 50),
                Thread(SAVED, R.array.demo_chat_saved, 60 * 6),
            )
            val rawMessages = threads.flatMap { thread ->
                val items = resources.getStringArray(thread.array)
                val end = now.minus(Duration.ofMinutes(thread.minutesAgo))
                items.mapIndexed { index, item ->
                    val author = item.substringBefore('|')
                    Message(
                        id = "${thread.id}-$index",
                        conversationId = thread.id,
                        authorId = if (author == "me") ME else author,
                        text = item.substringAfter('|'),
                        sentAt = end.minus(Duration.ofMinutes((items.size - 1 - index) * 2L)),
                    )
                }
            }
            val reactions = mapOf(
                "$ANYA-2" to persistentListOf(Reaction("\u2764\uFE0F", 1, mine = true)),
                "$GROUP-5" to persistentListOf(Reaction("\uD83D\uDC4D", 2, mine = false)),
                "$ROOM_GENERAL-1" to persistentListOf(Reaction("\uD83D\uDD25", 3, mine = true)),
            )
            val replies = mapOf("$GROUP-4" to "$GROUP-3", "$ANYA-4" to "$ANYA-2")
            val messages = rawMessages.map { message ->
                message.copy(
                    reactions = reactions[message.id] ?: message.reactions,
                    replyToId = replies[message.id],
                )
            }
            val last = messages.groupBy { it.conversationId }.mapValues { it.value.last() }

            fun preview(id: String, showAuthor: Boolean): Preview? = last[id]?.let { message ->
                val author = when {
                    message.isOwn -> null
                    showAuthor -> byId[message.authorId]?.name?.substringBefore(' ')
                    else -> null
                }
                Preview(author, message.text)
            }

            fun updated(id: String) = last[id]?.sentAt ?: now

            fun faces(id: String) = messages.filter { it.conversationId == id && it.authorId != ME }
                .map { it.authorId }
                .distinct()
                .mapNotNull(byId::get)
                .take(3)
                .toImmutableList()

            val roomTitle = resources.getString(R.string.demo_title_room)
            val generalTitle = resources.getString(R.string.demo_channel_general)
            val designTitle = resources.getString(R.string.demo_channel_design)

            val conversations = listOf(
                Conversation(SAVED, ConversationKind.Saved, resources.getString(R.string.demo_saved_title), Protection.EndToEnd,
                    preview(SAVED, false), updated(SAVED), pinned = true),
                Conversation(ANYA, ConversationKind.Direct, byId.getValue(ANYA).name, Protection.EndToEnd,
                    preview(ANYA, false), updated(ANYA), unread = 2),
                Conversation(GROUP, ConversationKind.Group, resources.getString(R.string.demo_title_group), Protection.EndToEnd,
                    preview(GROUP, true), updated(GROUP), memberCount = 4, faces = faces(GROUP)),
                Conversation(
                    ROOM, ConversationKind.Room, roomTitle, Protection.EndToEnd,
                    preview(ROOM_GENERAL, true), updated(ROOM_GENERAL), unread = 3, memberCount = 6,
                    channels = persistentListOf(
                        RoomChannel(ROOM_GENERAL, generalTitle, RoomChannelKind.Text, unread = 3),
                        RoomChannel(ROOM_DESIGN, designTitle, RoomChannelKind.Text),
                        RoomChannel(
                            ROOM_VOICE, resources.getString(R.string.demo_channel_voice), RoomChannelKind.Voice,
                            participants = persistentListOf(byId.getValue("dima"), byId.getValue("katya"), byId.getValue("lera")),
                            speaking = persistentSetOf("katya"),
                        ),
                    ),
                ),
                Conversation(MISHA, ConversationKind.Direct, byId.getValue(MISHA).name, Protection.EndToEnd,
                    preview(MISHA, false), updated(MISHA)),
                Conversation(CHANNEL, ConversationKind.Channel, resources.getString(R.string.demo_title_channel), Protection.Open,
                    preview(CHANNEL, false), updated(CHANNEL), memberCount = 1280),
                Conversation(BOT, ConversationKind.Bot, byId.getValue(BOT).name, Protection.Open,
                    preview(BOT, false), updated(BOT)),
                Conversation(ROOM_GENERAL, ConversationKind.Room, generalTitle, Protection.EndToEnd,
                    preview(ROOM_GENERAL, true), updated(ROOM_GENERAL), memberCount = 6, parentId = ROOM),
                Conversation(ROOM_DESIGN, ConversationKind.Room, designTitle, Protection.EndToEnd,
                    null, now.minus(Duration.ofDays(2)), memberCount = 6, parentId = ROOM),
            ).sortedWith(DemoRepository.conversationOrder)

            return DemoSeed(
                contacts = contacts,
                conversations = conversations,
                messages = messages,
                echoBotConversationId = BOT,
                echoBotAuthorId = BOT,
                directContactIds = setOf(ANYA, MISHA, "lera", "dima", "katya"),
                bot = DemoBot(
                    greeting = resources.getString(R.string.demo_bot_greeting),
                    buttons = resources.getStringArray(R.array.demo_bot_buttons).toList(),
                    pressed = resources.getString(R.string.demo_bot_pressed),
                    commands = listOf(
                        BotCommand("start", resources.getString(R.string.demo_bot_command_start)),
                        BotCommand("help", resources.getString(R.string.demo_bot_command_help)),
                    ),
                ),
            )
        }
    }
}

/** What the demo echo bot says besides echoing; [pressed] takes the button text. */
data class DemoBot(
    val greeting: String = "",
    val buttons: List<String> = emptyList(),
    val pressed: String = "%1\$s",
    val commands: List<BotCommand> = emptyList(),
)
