package app.blesk.core.messaging

import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.ContactEntity
import app.blesk.core.data.db.ConversationEntity
import app.blesk.core.data.db.MessageEntity
import app.blesk.core.data.db.OutboxEntity
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.settings.Privacy
import app.blesk.core.media.MediaVault
import app.blesk.core.network.ApiException
import app.blesk.core.network.BleskApi
import app.blesk.core.network.Transport
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.Envelope
import app.blesk.shared.protocol.OutgoingMessage
import app.blesk.shared.protocol.ReceiptKind
import app.blesk.shared.protocol.ServerFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Chats on a real server. Every outgoing content is written to the encrypted outbox first and
 * only then encrypted and sent, so nothing is lost offline. Incoming envelopes are decrypted,
 * stored, and only then acknowledged, so the server keeps them until they are safe here.
 */
class ServerChatRepository(
    private val db: BleskDatabase,
    private val api: BleskApi,
    private val socket: Transport,
    private val crypto: MessageCrypto,
    private val keys: KeyManager,
    private val accounts: AccountManager,
    private val media: MediaVault,
    private val me: String,
    private val scope: CoroutineScope,
    private val privacy: () -> Privacy,
    private val profile: () -> Pair<String, String>,
    private val labels: (Attachment?) -> String,
    private val log: EventLog,
    private val clock: () -> Long = System::currentTimeMillis,
) : ChatRepository {
    private val typingState = MutableStateFlow<Set<String>>(emptySet())
    private val typingJobs = HashMap<String, Job>()
    private val flushLock = Mutex()
    private val contactCache = db.contacts().observeAll()
        .map { list -> list.associate { it.username to it.toModel() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override val typing: StateFlow<Set<String>> = typingState.asStateFlow()

    override val conversations: Flow<List<Conversation>> = db.conversations().observeAll().map { list ->
        list.filter { it.previewText != null }.map { it.toModel() }
    }

    override val contacts: Flow<List<Contact>> = db.contacts().observeAll().map { list -> list.map { it.toModel() } }

    override fun conversation(id: String): Flow<Conversation?> = db.conversations().observe(id).map { it?.toModel() }

    override fun messages(conversationId: String): Flow<List<Message>> =
        db.messages().observe(conversationId).map { list -> list.map { it.toModel(me) } }

    override fun contact(id: String): Contact? = contactCache.value[id]

    fun start() {
        scope.launch {
            if (!accounts.restore(api)) runCatching { accounts.signIn(api, me) }
            socket.start()
        }
        scope.launch { socket.frames.collect { frame -> handle(frame) } }
        scope.launch { socket.connected.filter { it }.collect { flush() } }
    }

    fun stop() = socket.stop()

    override suspend fun openDirect(username: String): String? {
        if (username == me) return null
        val existing = db.contacts().get(username)
        if (existing == null) {
            val account = runCatching { api.account(username) }.getOrNull() ?: return null
            db.contacts().upsert(ContactEntity(username = account.username, name = account.username, identityKey = account.identityKey))
        }
        ensureConversation(username)
        return username
    }

    override fun send(conversationId: String, text: String, replyToId: String?) {
        val body = text.trim()
        if (body.isEmpty()) return
        val content = Content(UUID.randomUUID().toString(), clock(), Body.Text(body, replyToId))
        scope.launch {
            db.messages().insert(
                MessageEntity(content.id, conversationId, me, body, content.sentAtMillis, SendStatus.Sending.name, replyToId),
            )
            setPreview(conversationId, null, body, content.sentAtMillis)
            enqueue(conversationId, content)
            log.record(EventCode.MessageQueued)
        }
    }

    override fun sendAttachment(conversationId: String, attachment: Attachment, caption: String, replyToId: String?) {
        val id = UUID.randomUUID().toString()
        val sentAt = clock()
        scope.launch {
            db.messages().insert(
                MessageEntity(
                    id, conversationId, me, caption.trim(), sentAt, SendStatus.Sending.name, replyToId,
                    attachment = encodeAttachment(attachment),
                ),
            )
            setPreview(conversationId, null, caption.trim().ifEmpty { labels(attachment) }, sentAt)
            val pointer = try {
                withContext(Dispatchers.IO) { Attachments.upload(api, attachment) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (pointer == null) {
                advanceStatus(listOf(id), SendStatus.Failed, force = true)
                log.record(EventCode.SendFailed)
                return@launch
            }
            enqueue(conversationId, Content(id, sentAt, Body.Text(caption.trim(), replyToId, pointer)))
        }
    }

    override fun toggleReaction(conversationId: String, messageId: String, symbol: String) {
        scope.launch {
            val message = db.messages().get(messageId) ?: return@launch
            val entries = decodeReactions(message.reactions)
            val mine = entries.any { it.symbol == symbol && me in it.users }
            db.messages().upsert(message.copy(reactions = encodeReactions(applyReaction(entries, me, symbol, remove = mine))))
            enqueue(conversationId, Content(UUID.randomUUID().toString(), clock(), Body.Reaction(messageId, symbol, remove = mine)))
        }
    }

    override fun edit(conversationId: String, messageId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        scope.launch {
            val message = db.messages().get(messageId)?.takeIf { it.authorId == me } ?: return@launch
            db.messages().upsert(message.copy(text = body, edited = true))
            refreshPreview(conversationId)
            enqueue(conversationId, Content(UUID.randomUUID().toString(), clock(), Body.Edit(messageId, body)))
        }
    }

    override fun delete(conversationId: String, messageId: String) {
        scope.launch {
            db.messages().get(messageId)?.takeIf { it.authorId == me } ?: return@launch
            db.messages().delete(messageId)
            refreshPreview(conversationId)
            enqueue(conversationId, Content(UUID.randomUUID().toString(), clock(), Body.Delete(messageId)))
        }
    }

    override fun markRead(conversationId: String) {
        scope.launch {
            db.conversations().markRead(conversationId)
            val ids = db.messages().unreadIncoming(conversationId, me)
            if (ids.isEmpty()) return@launch
            db.messages().markIncomingRead(ids)
            if (privacy().readReceipts) {
                enqueue(conversationId, Content(UUID.randomUUID().toString(), clock(), Body.Receipt(ReceiptKind.Read, ids)))
            }
        }
    }

    override fun setTyping(conversationId: String, active: Boolean) {
        if (!privacy().typingIndicator) return
        scope.launch {
            if (!crypto.hasSessionSafe(conversationId)) return@launch
            runCatching {
                val content = Content(UUID.randomUUID().toString(), clock(), Body.Typing(active))
                val sealed = withContext(Dispatchers.IO) { crypto.encrypt(conversationId, content) }
                api.send(conversationId, OutgoingMessage(content.id, sealed.type, sealed.body, ephemeral = true))
            }
        }
    }

    // Outgoing

    private suspend fun enqueue(recipient: String, content: Content) {
        val json = storageJson.encodeToString(Content.serializer(), content)
        db.outbox().insert(OutboxEntity(recipient = recipient, content = json, createdAt = clock()))
        flush()
    }

    private suspend fun flush() {
        if (!flushLock.tryLock()) return
        try {
            for (item in db.outbox().all()) {
                if (!deliver(item)) break
            }
        } finally {
            flushLock.unlock()
        }
    }

    /** Returns false when the network is down and the rest of the queue should wait. */
    private suspend fun deliver(item: OutboxEntity): Boolean {
        val content = storageJson.decodeFromString(Content.serializer(), item.content)
        return try {
            if (!crypto.hasSessionSafe(item.recipient)) {
                val bundle = api.bundle(item.recipient)
                if (bundle == null) {
                    dropFailed(item, content)
                    return true
                }
                withContext(Dispatchers.IO) { crypto.startSession(bundle) }
                shareProfile(item.recipient)
            }
            val sealed = withContext(Dispatchers.IO) { crypto.encrypt(item.recipient, content) }
            api.send(item.recipient, OutgoingMessage(content.id, sealed.type, sealed.body, item.ephemeral))
            db.outbox().delete(item.id)
            if (content.body is Body.Text) advanceStatus(listOf(content.id), SendStatus.Sent)
            true
        } catch (e: ApiException) {
            when {
                e.status == UNAUTHORIZED -> {
                    runCatching { accounts.signIn(api, me) }
                    false
                }
                e.status in CLIENT_ERRORS && e.status != TOO_MANY -> {
                    dropFailed(item, content)
                    true
                }
                else -> false
            }
        } catch (_: IOException) {
            db.outbox().bumpAttempts(item.id)
            scheduleRetry()
            false
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Broken keys or an untrusted identity: this item can never go out as it is.
            dropFailed(item, content)
            true
        }
    }

    private fun scheduleRetry() {
        scope.launch {
            delay(RETRY_MILLIS)
            flush()
        }
    }

    private suspend fun dropFailed(item: OutboxEntity, content: Content) {
        db.outbox().delete(item.id)
        if (content.body is Body.Text) advanceStatus(listOf(content.id), SendStatus.Failed, force = true)
        log.record(EventCode.SendFailed)
    }

    /** Your name and bio go to a person once, when the encrypted session with them starts. */
    private suspend fun shareProfile(recipient: String) {
        val (name, bio) = profile()
        if (name.isBlank() && bio.isBlank()) return
        val content = Content(UUID.randomUUID().toString(), clock(), Body.Profile(name, bio))
        val sealed = withContext(Dispatchers.IO) { crypto.encrypt(recipient, content) }
        api.send(recipient, OutgoingMessage(content.id, sealed.type, sealed.body))
    }

    // Incoming

    private suspend fun handle(frame: ServerFrame) {
        when (frame) {
            is ServerFrame.Deliver -> receive(frame.envelope)
            is ServerFrame.KeysLow -> refillKeys(frame.preKeys, frame.kyberPreKeys)
            ServerFrame.Synced -> log.record(EventCode.ServerSynced)
        }
    }

    private suspend fun receive(envelope: Envelope) {
        val content = try {
            withContext(Dispatchers.IO) { crypto.decrypt(envelope) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            log.record(EventCode.DecryptFailed)
            socket.ack(envelope.serverId)
            return
        }
        apply(envelope.sender, content)
        socket.ack(envelope.serverId)
    }

    private suspend fun apply(sender: String, content: Content) {
        when (val body = content.body) {
            is Body.Text -> receiveText(sender, content, body)
            is Body.Receipt -> advanceStatus(
                body.ids,
                if (body.kind == ReceiptKind.Read) SendStatus.Read else SendStatus.Delivered,
            )
            is Body.Typing -> showTyping(sender, body.active)
            is Body.Reaction -> db.messages().get(body.target)?.let { message ->
                val entries = applyReaction(decodeReactions(message.reactions), sender, body.symbol, body.remove)
                db.messages().upsert(message.copy(reactions = encodeReactions(entries)))
            }
            is Body.Edit -> db.messages().get(body.target)?.takeIf { it.authorId == sender }?.let {
                db.messages().upsert(it.copy(text = body.text, edited = true))
                refreshPreview(sender)
            }
            is Body.Delete -> db.messages().get(body.target)?.takeIf { it.authorId == sender }?.let {
                db.messages().delete(it.id)
                refreshPreview(sender)
            }
            is Body.Profile -> {
                val known = db.contacts().get(sender)
                val updated = (known ?: ContactEntity(sender, sender)).copy(name = body.name.take(MAX_NAME), bio = body.bio.take(MAX_BIO))
                db.contacts().upsert(updated)
                db.conversations().get(sender)?.let { db.conversations().upsert(it.copy(title = body.name.ifBlank { sender })) }
            }
        }
    }

    private suspend fun receiveText(sender: String, content: Content, body: Body.Text) {
        if (db.contacts().get(sender) == null) db.contacts().upsert(ContactEntity(sender, sender))
        ensureConversation(sender)
        showTyping(sender, false)
        val placeholder = body.attachment?.let(Attachments::placeholder)
        val inserted = db.messages().insert(
            MessageEntity(
                id = content.id,
                conversationId = sender,
                authorId = sender,
                text = body.text,
                sentAt = content.sentAtMillis,
                status = SendStatus.Delivered.name,
                replyToId = body.replyTo,
                attachment = encodeAttachment(placeholder),
            ),
        )
        if (inserted == -1L) return
        db.conversations().incrementUnread(sender)
        setPreview(sender, null, body.text.ifEmpty { labels(placeholder) }, content.sentAtMillis)
        enqueue(sender, Content(UUID.randomUUID().toString(), clock(), Body.Receipt(ReceiptKind.Delivered, listOf(content.id))))
        body.attachment?.let { pointer ->
            scope.launch {
                val local = runCatching { withContext(Dispatchers.IO) { Attachments.download(api, media, pointer) } }
                    .getOrNull()
                val stored = db.messages().get(content.id)
                if (local != null && stored != null) db.messages().upsert(stored.copy(attachment = encodeAttachment(local)))
            }
        }
    }

    private fun showTyping(sender: String, active: Boolean) {
        typingJobs.remove(sender)?.cancel()
        if (!active) {
            typingState.update { it - sender }
            return
        }
        typingState.update { it + sender }
        typingJobs[sender] = scope.launch {
            delay(TYPING_TIMEOUT_MILLIS)
            typingState.update { it - sender }
        }
    }

    private suspend fun refillKeys(preKeys: Int, kyber: Int) {
        runCatching {
            val upload = withContext(Dispatchers.IO) { keys.refill(preKeys, kyber) }
            api.uploadKeys(upload)
            log.record(EventCode.KeysRefilled)
        }
    }

    // Local bookkeeping

    private suspend fun ensureConversation(username: String) {
        if (db.conversations().get(username) != null) return
        val name = db.contacts().get(username)?.name?.ifBlank { null } ?: username
        db.conversations().upsert(
            ConversationEntity(
                id = username,
                kind = ConversationKind.Direct.name,
                title = name,
                protection = Protection.EndToEnd.name,
                peer = username,
                updatedAt = clock(),
            ),
        )
    }

    private suspend fun setPreview(conversationId: String, author: String?, text: String, at: Long) {
        ensureConversation(conversationId)
        db.conversations().setPreview(conversationId, author, text, at)
    }

    private suspend fun refreshPreview(conversationId: String) {
        val last = db.messages().last(conversationId)
        val text = last?.let { it.text.ifEmpty { labels(decodeAttachment(it.attachment)) } }
        db.conversations().setPreview(conversationId, null, text ?: "", last?.sentAt ?: 0)
    }

    /** Statuses only move forward, except an explicit failure. */
    private suspend fun advanceStatus(ids: List<String>, status: SendStatus, force: Boolean = false) {
        ids.forEach { id ->
            val message = db.messages().get(id)?.takeIf { it.authorId == me } ?: return@forEach
            val current = runCatching { SendStatus.valueOf(message.status) }.getOrDefault(SendStatus.Sending)
            if (force || status.ordinal > current.ordinal) db.messages().upsert(message.copy(status = status.name))
        }
    }

    private suspend fun MessageCrypto.hasSessionSafe(username: String) = withContext(Dispatchers.IO) { hasSession(username) }

    private companion object {
        const val UNAUTHORIZED = 401
        const val TOO_MANY = 429
        val CLIENT_ERRORS = 400..499
        const val RETRY_MILLIS = 15_000L
        const val TYPING_TIMEOUT_MILLIS = 6_000L
        const val MAX_NAME = 64
        const val MAX_BIO = 140
    }
}

internal fun applyReaction(entries: List<ReactionEntry>, user: String, symbol: String, remove: Boolean): List<ReactionEntry> {
    val others = entries.map { it.copy(users = it.users - user) }
    val updated = if (remove) {
        others
    } else {
        val existing = others.firstOrNull { it.symbol == symbol }
        if (existing == null) {
            others + ReactionEntry(symbol, listOf(user))
        } else {
            others.map { if (it.symbol == symbol) it.copy(users = it.users + user) else it }
        }
    }
    return updated.filter { it.users.isNotEmpty() }
}
