package app.blesk.navigation

import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.RoomChannelKind

/**
 * The chat to open for [this] conversation. A room has no chat of its own, only its channels, so it
 * opens on its first text channel, as «# общее» does on the home screen; anything else is itself.
 */
internal fun Conversation.chatTarget(): String =
    if (kind == ConversationKind.Room) channels.firstOrNull { it.kind == RoomChannelKind.Text }?.id ?: id else id
