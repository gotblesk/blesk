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
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.data.model.RoomChannelKind
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import java.time.Duration
import java.time.Instant

/** The initial demo world, built from string resources so all text stays translatable. */
class DemoSeed(
    val contacts: List<Contact>,
    val conversations: List<Conversation>,
    val messages: List<Message>,
    val echoBotConversationId: String,
    val echoBotAuthorId: String,
) {
    companion object {
        const val ANYA = "anya"
        const val MISHA = "misha"
        const val GROUP = "group"
        const val ROOM = "room"
        const val CHANNEL = "channel"
        const val BOT = "bot"

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
                Thread(ROOM, R.array.demo_chat_room, 70),
                Thread(MISHA, R.array.demo_chat_misha, 180),
                Thread(CHANNEL, R.array.demo_chat_channel, 60 * 26),
                Thread(BOT, R.array.demo_chat_bot, 60 * 50),
            )
            val messages = threads.flatMap { thread ->
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

            val conversations = listOf(
                Conversation(ANYA, ConversationKind.Direct, byId.getValue(ANYA).name, Protection.EndToEnd,
                    preview(ANYA, false), updated(ANYA), unread = 2),
                Conversation(GROUP, ConversationKind.Group, resources.getString(R.string.demo_title_group), Protection.EndToEnd,
                    preview(GROUP, true), updated(GROUP), memberCount = 4),
                Conversation(
                    ROOM, ConversationKind.Room, resources.getString(R.string.demo_title_room), Protection.EndToEnd,
                    preview(ROOM, true), updated(ROOM), unread = 3, memberCount = 6,
                    channels = persistentListOf(
                        RoomChannel("room-general", resources.getString(R.string.demo_channel_general), RoomChannelKind.Text, unread = 3),
                        RoomChannel("room-design", resources.getString(R.string.demo_channel_design), RoomChannelKind.Text),
                        RoomChannel(
                            "room-voice", resources.getString(R.string.demo_channel_voice), RoomChannelKind.Voice,
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
            ).sortedWith(DemoRepository.conversationOrder)

            return DemoSeed(contacts, conversations, messages, echoBotConversationId = BOT, echoBotAuthorId = BOT)
        }
    }
}
