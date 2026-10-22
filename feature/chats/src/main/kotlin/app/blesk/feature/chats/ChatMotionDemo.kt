package app.blesk.feature.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.GLINT_REACTION
import app.blesk.core.data.model.ME
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Reaction
import app.blesk.core.data.model.SendStatus
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.LocalClock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import java.time.Instant

/** The chat's signature animations from the brief, each playable on its own in the motion showcase. */
enum class ChatMotion { Send, Receive, Status, Thread, Reaction, Delete, Melt, Failure, Unread, ReadMore }

/**
 * One chat animation on real chat components with made-up messages. A new [replay] plays it again
 * from the start.
 */
@Composable
fun ChatMotionDemo(motion: ChatMotion, replay: Int, modifier: Modifier = Modifier) {
    val clock = LocalClock.current
    key(replay) {
        val now = remember { clock() }
        Column(modifier.fillMaxWidth().heightIn(min = 72.dp), verticalArrangement = Arrangement.Bottom) {
            when (motion) {
                ChatMotion.Send -> Send(now)
                ChatMotion.Receive -> Receive(now)
                ChatMotion.Status -> Status(now)
                ChatMotion.Thread -> Thread(now)
                ChatMotion.Reaction -> Reaction(now)
                ChatMotion.Delete -> Delete(now)
                ChatMotion.Melt -> Melt(now)
                ChatMotion.Failure -> Failure(now)
                ChatMotion.Unread -> Unread()
                ChatMotion.ReadMore -> ReadMore(now)
            }
        }
    }
}

private val QuietActions = MessageActions(onReply = null, onMenu = {}, onReact = { _, _ -> }, onApplyTheme = {})

private fun demo(at: Instant, id: String, own: Boolean, text: String, status: SendStatus = SendStatus.Read) = Message(
    id = id,
    conversationId = "demo",
    authorId = if (own) ME else "anya",
    text = text,
    sentAt = at,
    status = status,
)

@Composable
private fun Messages(messages: ImmutableList<Message>, fresh: Set<String> = emptySet(), quotes: Map<String, String> = emptyMap()) {
    MessageColumn(messages = messages, author = null, fresh = fresh, quoteText = { quotes[it] }, actions = QuietActions)
}

@Composable
private fun Send(now: Instant) {
    val first = demo(now, "a", own = true, text = "Привет!")
    var sent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STEP)
        sent = true
    }
    val messages = if (sent) persistentListOf(first, demo(now, "b", own = true, text = "Как дела?")) else persistentListOf(first)
    Messages(messages, fresh = setOf("b"))
}

@Composable
private fun Receive(now: Instant) {
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(TYPING)
        arrived = true
    }
    if (arrived) Messages(persistentListOf(demo(now, "a", own = false, text = "Уже еду")), fresh = setOf("a")) else TypingDots()
}

@Composable
private fun Status(now: Instant) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(STATUSES.size - 1) {
            delay(STEP)
            step++
        }
    }
    Messages(persistentListOf(demo(now, "a", own = true, text = "Отправлено, доставлено, прочитано", status = STATUSES[step])))
}

@Composable
private fun Thread(now: Instant) {
    var replied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STEP)
        replied = true
    }
    Messages(persistentListOf(demo(now, "q", own = false, text = "Во сколько встречаемся?")))
    if (replied) {
        val reply = demo(now, "r", own = true, text = "В семь, у входа").copy(replyToId = "q")
        Messages(persistentListOf(reply), fresh = setOf("r"), quotes = mapOf("q" to "Аня: Во сколько встречаемся?"))
    }
}

@Composable
private fun Reaction(now: Instant) {
    var reacted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STEP)
        reacted = true
    }
    val reactions = if (reacted) persistentListOf(Reaction(GLINT_REACTION, 1, mine = true)) else persistentListOf()
    Messages(persistentListOf(demo(now, "a", own = false, text = "Смотри, какой закат").copy(reactions = reactions)))
}

@Composable
private fun Delete(now: Instant) {
    var messages by remember {
        mutableStateOf(listOf(demo(now, "a", true, "Первое"), demo(now, "b", true, "Это удалится"), demo(now, "c", true, "Третье")))
    }
    LaunchedEffect(Unit) {
        delay(STEP)
        messages = messages.filterNot { it.id == "b" }
    }
    val motion = Blesk.motion
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 220.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        userScrollEnabled = false,
    ) {
        items(messages, key = { it.id }) { message ->
            Column(
                Modifier.animateItem(fadeInSpec = null, placementSpec = motion.settle(), fadeOutSpec = motion.transition()),
            ) { Messages(persistentListOf(message)) }
        }
    }
}

@Composable
private fun Melt(now: Instant) {
    val message = demo(now, "a", own = false, text = "Исчезнет через восемь секунд")
        .copy(expiresAt = now.plusSeconds(MELT_SECONDS))
    Messages(persistentListOf(message))
}

@Composable
private fun Failure(now: Instant) {
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STEP * 2)
        failed = true
    }
    val messages = listOf(
        demo(now, "a", own = true, text = "Это дошло"),
        demo(now, "b", own = true, text = "А это нет", status = if (failed) SendStatus.Failed else SendStatus.Sending),
    )
    Messages(messages.toImmutableList())
}

@Composable
private fun Unread() {
    var count by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(UNREAD) {
            delay(STEP / 2)
            count++
        }
        delay(STEP * 2)
        count = 0
    }
    if (count > 0) UnreadBadge(count)
}

@Composable
private fun ReadMore(now: Instant) {
    val text = List(LONG_LINES) { "Длинное сообщение раскрывается плавно, а соседние сдвигаются без скачка." }.joinToString(" ")
    Messages(persistentListOf(demo(now, "a", own = false, text = text)))
}

private val STATUSES = listOf(SendStatus.Sending, SendStatus.Sent, SendStatus.Delivered, SendStatus.Read)
private const val STEP = 700L
private const val TYPING = 1_200L
private const val MELT_SECONDS = 8L
private const val UNREAD = 5
private const val LONG_LINES = 8
