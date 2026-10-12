package app.blesk.core.messaging

import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.data.BotAccounts
import app.blesk.core.data.BotController
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.GroupController
import app.blesk.core.data.IncomingMessages
import app.blesk.core.data.KeyVerification
import app.blesk.core.data.SpaceController
import app.blesk.core.data.SpaceResult
import app.blesk.core.data.calls.CallSignal
import app.blesk.core.data.calls.CallSignaling
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
import app.blesk.core.data.settings.IncomingNote
import app.blesk.core.data.settings.Privacy
import app.blesk.core.media.MediaVault
import app.blesk.core.network.ApiException
import app.blesk.core.network.BleskApi
import app.blesk.core.network.Transport
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.CallToken
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.CreateSpace
import app.blesk.shared.protocol.Envelope
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.InviteLink
import app.blesk.shared.protocol.OutgoingMessage
import app.blesk.shared.protocol.ReceiptKind
import app.blesk.shared.protocol.RoleChange
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceRole
import app.blesk.shared.protocol.Spaces
import app.blesk.shared.protocol.UpdateSpace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    groupCrypto: GroupCrypto,
    private val keys: KeyManager,
    private val accounts: AccountManager,
    private val media: MediaVault,
    override val me: String,
    private val scope: CoroutineScope,
    private val privacy: () -> Privacy,
    private val profile: () -> Pair<String, String>,
    private val labels: (Attachment?) -> String,
    private val log: EventLog,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The title of «Избранное», your notes to yourself; it lives under your own username. */
    private val savedTitle: String = "",
) : ChatRepository,
    GroupController,
    SpaceController,
    CallSignaling,
    BotController,
    IncomingMessages,
    KeyVerification by KeyChecks(db, crypto, scope, log),
    BotAccounts by BotOwner(api) {
    private val incomingFlow = MutableSharedFlow<IncomingNote>(extraBufferCapacity = SIGNAL_BUFFER)
    override val incoming: SharedFlow<IncomingNote> = incomingFlow.asSharedFlow()
    private val spaces = SpaceEngine(db, api, media, me, labels)
    private val bots =
        BotEngine(db, api, me, clock) { bot, title, text -> incomingFlow.tryEmit(IncomingNote(bot, title, null, text)) }
    private val groups = GroupEngine(
        db = db,
        crypto = groupCrypto,
        me = me,
        outbox = object : GroupOutbox {
            override suspend fun direct(recipient: String, content: Content) = enqueue(recipient, content)
            override suspend fun sealed(recipient: String, content: Content, type: Int, body: String) =
                enqueueSealed(recipient, content, type, body)
        },
        clock = clock,
    )
    private val signals = MutableSharedFlow<CallSignal>(extraBufferCapacity = SIGNAL_BUFFER)
    private val typingState = MutableStateFlow<Set<String>>(emptySet())
    private val typingJobs = HashMap<String, Job>()
    private val flushLock = Mutex()
    private val contactCache = db.contacts().observeAll()
        .map { list -> list.associate { it.username to it.toModel() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override val typing: StateFlow<Set<String>> = typingState.asStateFlow()

    override val conversations: Flow<List<Conversation>> =
        combine(db.conversations().observeAll(), db.groups().observeAll(), spaces.spaces) { list, groupRows, spaceInfo ->
            val states = groupRows.associate { it.id to storageJson.decodeFromString(GroupState.serializer(), it.state) }
            list.filter { it.previewText != null && it.parentId == null }.map { row ->
                val space = spaces.handleOf(row.id)?.let(spaceInfo::get)
                if (space != null) row.toModel(space) else row.toModel(states[row.id])
            }
        }

    override val contacts: Flow<List<Contact>> = db.contacts().observeAll().map { list -> list.map { it.toModel() } }

    override fun conversation(id: String): Flow<Conversation?> =
        combine(
            db.conversations().observe(id),
            group(id.substringBefore(GroupEngine.CHANNEL_SEPARATOR)),
            spaces.spaces,
        ) { row, state, spaceInfo ->
            val space = spaces.handleOf(id)?.let(spaceInfo::get)?.takeIf { row?.parentId == null }
            when {
                row == null -> null
                space != null -> row.toModel(space)
                else -> row.toModel(state?.takeIf { row.parentId == null })
            }
        }

    override fun messages(conversationId: String): Flow<List<Message>> =
        db.messages().observe(conversationId).map { list -> list.map { it.toModel(me) } }

    override fun contact(id: String): Contact? = contactCache.value[id]

    /**
     * SQLite folds case for latin letters only, so the query is also tried lowercased and
     * capitalised; that covers how people actually type in Cyrillic.
     */
    override suspend fun search(query: String): List<Message> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val variants = setOf(needle, needle.lowercase(), needle.lowercase().replaceFirstChar { it.uppercase() })
        return variants
            .flatMap { db.messages().search(escapeLike(it)) }
            .distinctBy { it.id }
            .sortedByDescending { it.sentAt }
            .take(SEARCH_LIMIT)
            .map { it.toModel(me) }
    }

    private fun escapeLike(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    fun start() {
        scope.launch {
            if (!accounts.restore(api)) runCatching { accounts.signIn(api, me) }
            socket.start()
        }
        scope.launch { socket.frames.collect { frame -> handle(frame) } }
        scope.launch { socket.connected.filter { it }.collect { flush() } }
        scope.launch { sweep() }
        scope.launch { ensureSaved() }
    }

    fun stop() = socket.stop()

    override suspend fun openDirect(username: String): String? {
        if (username == me) return null
        if (bots.isBot(username)) return bots.open(username)
        val existing = db.contacts().get(username)
        if (existing == null) {
            val account = runCatching { api.account(username) }.getOrNull() ?: return null
            db.contacts().upsert(ContactEntity(username = account.username, name = account.username, identityKey = account.identityKey))
        }
        ensureConversation(username)
        return username
    }

    override fun send(conversationId: String, text: String, replyToId: String?) {
        if (bots.isBot(conversationId)) {
            scope.launch { bots.send(conversationId, text.trim(), replyToId) }
            return
        }
        val body = text.trim()
        if (body.isEmpty()) return
        if (spaces.handleOf(conversationId) != null) {
            scope.launch {
                runCatching { spaces.send(conversationId, body, replyToId, null) }.onFailure { log.record(EventCode.SendFailed) }
            }
            return
        }
        scope.launch { post(conversationId, body, replyToId, attachment = null, forwarded = false) }
    }

    override fun sendAttachment(conversationId: String, attachment: Attachment, caption: String, replyToId: String?) {
        // Bots take text only; files to a program in the clear are not worth the risk.
        if (bots.isBot(conversationId)) return
        if (spaces.handleOf(conversationId) != null) {
            scope.launch {
                runCatching { spaces.send(conversationId, caption, replyToId, attachment) }.onFailure { log.record(EventCode.SendFailed) }
            }
            return
        }
        scope.launch { post(conversationId, caption.trim(), replyToId, attachment, forwarded = false) }
    }

    override fun forward(conversationId: String, messageId: String, to: List<String>) {
        scope.launch {
            val original = db.messages().get(messageId)?.takeIf { it.conversationId == conversationId } ?: return@launch
            val attachment = decodeAttachment(original.attachment)
            to.distinct().forEach { target ->
                when {
                    bots.isBot(target) -> if (original.text.isNotBlank()) bots.send(target, original.text, null)
                    spaces.handleOf(target) != null -> runCatching { spaces.send(target, original.text, null, attachment) }
                    else -> post(target, original.text, null, attachment, forwarded = true)
                }
            }
        }
    }

    /** Stores, then encrypts and sends one of your messages; with the chat's timer if it has one. */
    private suspend fun post(conversationId: String, text: String, replyToId: String?, attachment: Attachment?, forwarded: Boolean) {
        if (text.isEmpty() && attachment == null) return
        val id = UUID.randomUUID().toString()
        val sentAt = clock()
        // Room channels follow the room's timer.
        val row = db.conversations().get(conversationId)
        val timer = row?.parentId?.let { db.conversations().get(it)?.timerSeconds } ?: row?.timerSeconds ?: 0
        db.messages().insert(
            MessageEntity(
                id, conversationId, me, text, sentAt, SendStatus.Sending.name, replyToId,
                attachment = encodeAttachment(attachment),
                expiresAt = expiry(sentAt, timer),
                forwardedFrom = if (forwarded) FORWARDED else null,
            ),
        )
        setPreview(conversationId, null, text.ifEmpty { labels(attachment) }, sentAt)
        if (conversationId == me) {
            advanceStatus(listOf(id), SendStatus.Read)
            return
        }
        val pointer = if (attachment == null) {
            null
        } else {
            try {
                withContext(Dispatchers.IO) { Attachments.upload(api, attachment) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                advanceStatus(listOf(id), SendStatus.Failed, force = true)
                log.record(EventCode.SendFailed)
                return
            }
        }
        dispatch(conversationId, Content(id, sentAt, Body.Text(text, replyToId, pointer, forwarded), expiresInSeconds = timer))
        log.record(EventCode.MessageQueued)
    }

    override fun setArchived(conversationId: String, archived: Boolean) {
        scope.launch { db.conversations().setArchived(conversationId, archived) }
    }

    override fun mute(conversationId: String, untilMillis: Long) {
        scope.launch { db.conversations().setMuted(conversationId, untilMillis) }
    }

    override fun markUnread(conversationId: String, unread: Boolean) {
        scope.launch { db.conversations().setMarkedUnread(conversationId, unread) }
    }

    /** «Избранное» is a chat with yourself: always there, pinned, and never sent anywhere. */
    private suspend fun ensureSaved() {
        if (savedTitle.isEmpty() || db.conversations().get(me) != null) return
        db.conversations().upsert(
            ConversationEntity(
                id = me,
                kind = ConversationKind.Saved.name,
                title = savedTitle,
                protection = Protection.EndToEnd.name,
                peer = null,
                updatedAt = clock(),
                pinned = true,
                previewText = "",
            ),
        )
    }

    override fun saveDraft(conversationId: String, text: String) {
        scope.launch { db.conversations().setDraft(conversationId, text.take(Bots.MAX_TEXT)) }
    }

    override fun setChatPinned(conversationId: String, pinned: Boolean) {
        scope.launch { db.conversations().setPinned(conversationId, pinned) }
    }

    override fun pinMessage(conversationId: String, messageId: String?) {
        scope.launch { db.conversations().pinMessage(conversationId, messageId) }
    }

    override fun setTimer(conversationId: String, seconds: Int) {
        if (bots.isBot(conversationId) || spaces.handleOf(conversationId) != null) return
        scope.launch {
            db.conversations().setTimer(conversationId, seconds)
            dispatch(conversationId, Content(UUID.randomUUID().toString(), clock(), Body.Timer(seconds)))
        }
    }

    /** Deletes disappearing messages when their time comes, files included. */
    private suspend fun sweep() {
        while (true) {
            val now = clock()
            db.messages().expiredMessages(now).forEach { media.forget(decodeAttachment(it.attachment)) }
            val touched = db.messages().expiredConversations(now)
            db.messages().deleteExpired(now)
            touched.forEach { refreshPreview(it) }
            val next = db.messages().nextExpiry()
            delay(((next ?: Long.MAX_VALUE) - clock()).coerceIn(SWEEP_MIN_MILLIS, SWEEP_MAX_MILLIS))
        }
    }

    private fun expiry(sentAt: Long, seconds: Int): Long? = if (seconds > 0) sentAt + seconds * MILLIS_PER_SECOND else null

    override fun toggleReaction(conversationId: String, messageId: String, symbol: String) {
        if (spaces.handleOf(conversationId) != null) return
        scope.launch {
            val message = db.messages().get(messageId) ?: return@launch
            val entries = decodeReactions(message.reactions)
            val mine = entries.any { it.symbol == symbol && me in it.users }
            db.messages().upsert(message.copy(reactions = encodeReactions(applyReaction(entries, me, symbol, remove = mine))))
            dispatch(conversationId, Content(UUID.randomUUID().toString(), clock(), Body.Reaction(messageId, symbol, remove = mine)))
        }
    }

    override fun edit(conversationId: String, messageId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        if (spaces.handleOf(conversationId) != null) {
            scope.launch { runCatching { spaces.edit(messageId, body) } }
            return
        }
        scope.launch {
            val message = db.messages().get(messageId)?.takeIf { it.authorId == me } ?: return@launch
            db.messages().upsert(message.copy(text = body, edited = true))
            refreshPreview(conversationId)
            dispatch(conversationId, Content(UUID.randomUUID().toString(), clock(), Body.Edit(messageId, body)))
        }
    }

    override fun delete(conversationId: String, messageId: String) {
        if (spaces.handleOf(conversationId) != null) {
            scope.launch { runCatching { spaces.delete(messageId) } }
            return
        }
        scope.launch {
            val message = db.messages().get(messageId) ?: return@launch
            val moderator = groups.groupOf(conversationId)?.isAdmin(me) == true
            if (message.authorId != me && !moderator) return@launch
            db.messages().delete(messageId)
            refreshPreview(conversationId)
            dispatch(conversationId, Content(UUID.randomUUID().toString(), clock(), Body.Delete(messageId)))
        }
    }

    override fun markRead(conversationId: String) {
        scope.launch {
            db.conversations().markRead(conversationId)
            val ids = db.messages().unreadIncoming(conversationId, me)
            if (ids.isEmpty()) return@launch
            db.messages().markIncomingRead(ids)
            val direct = groups.groupOf(conversationId) == null && !isSpace(conversationId) && !bots.isBot(conversationId)
            if (privacy().readReceipts && direct) {
                enqueue(conversationId, Content(UUID.randomUUID().toString(), clock(), Body.Receipt(ReceiptKind.Read, ids)))
            }
        }
    }

    override fun setTyping(conversationId: String, active: Boolean) {
        if (!privacy().typingIndicator) return
        scope.launch {
            if (groups.groupOf(conversationId) != null || !crypto.hasSessionSafe(conversationId)) return@launch
            runCatching {
                val content = Content(UUID.randomUUID().toString(), clock(), Body.Typing(active))
                val sealed = withContext(Dispatchers.IO) { crypto.encrypt(conversationId, content) }
                api.send(conversationId, OutgoingMessage(content.id, sealed.type, sealed.body, ephemeral = true))
            }
        }
    }

    // Groups and rooms

    override fun group(id: String): Flow<GroupState?> = db.groups().observeAll().map { rows ->
        rows.firstOrNull { it.id == id }?.let { storageJson.decodeFromString(GroupState.serializer(), it.state) }
    }

    override suspend fun createGroup(kind: GroupKind, title: String, members: List<String>): String {
        val id = groups.create(kind, title, members)
        flush()
        return id
    }

    override suspend fun rename(groupId: String, title: String) = groups.rename(groupId, title).also { flush() }

    override suspend fun addMembers(groupId: String, usernames: List<String>) = groups.addMembers(groupId, usernames).also { flush() }

    override suspend fun removeMember(groupId: String, username: String) = groups.remove(groupId, username).also { flush() }

    override suspend fun setRole(groupId: String, username: String, role: GroupRole) =
        groups.setRole(groupId, username, role).also { flush() }

    override suspend fun addChannel(groupId: String, title: String, kind: GroupChannelKind, adminsOnly: Boolean) =
        groups.addChannel(groupId, title, kind, adminsOnly).also { flush() }

    override suspend fun removeChannel(groupId: String, channelId: String) = groups.removeChannel(groupId, channelId).also { flush() }

    override suspend fun setInvite(groupId: String, enabled: Boolean): String? {
        val code = groups.setInvite(groupId, enabled)
        flush()
        return code?.let { InviteLink.format(groupId, me, it) }
    }

    override suspend fun leave(groupId: String) {
        groups.leave(groupId)
        flush()
    }

    override suspend fun join(link: String): Boolean {
        val invite = InviteLink.parse(link) ?: return false
        if (openDirect(invite.admin) == null) return false
        enqueue(invite.admin, Content(UUID.randomUUID().toString(), clock(), Body.JoinRequest(invite.groupId, invite.code)))
        return true
    }

    // Public spaces

    override fun space(conversationId: String): Flow<SpaceInfo?> =
        spaces.spaces.map { all -> spaces.handleOf(conversationId)?.let(all::get) }

    override fun isSpace(conversationId: String) = spaces.handleOf(conversationId) != null

    override suspend fun searchSpaces(query: String): List<SpaceInfo> =
        runCatching { api.searchSpaces(query.trim().removePrefix("@")) }.getOrDefault(emptyList())

    override suspend fun joinSpace(handle: String): String? = runCatching {
        val info = api.joinSpace(handle)
        spaces.remember(info)
        runCatching { spaces.sync() }
        SpaceEngine.PREFIX + info.handle
    }.getOrNull()

    override suspend fun createSpace(kind: SpaceKind, handle: String, title: String, about: String): Pair<SpaceResult, String?> {
        if (!Spaces.isValidHandle(handle)) return SpaceResult.HandleInvalid to null
        return try {
            val info = api.createSpace(CreateSpace(handle, kind, title.trim().take(Spaces.MAX_TITLE), about.trim().take(Spaces.MAX_ABOUT)))
            spaces.remember(info)
            SpaceResult.Ok to SpaceEngine.PREFIX + info.handle
        } catch (e: ApiException) {
            (if (e.code == "handle_taken") SpaceResult.HandleTaken else SpaceResult.Failed) to null
        } catch (_: IOException) {
            SpaceResult.Failed to null
        }
    }

    override suspend fun updateSpace(handle: String, title: String, about: String) =
        runCatching { spaces.remember(api.updateSpace(handle, UpdateSpace(title, about))) }.isSuccess

    override suspend fun leaveSpace(handle: String) {
        runCatching { api.leaveSpace(handle) }
        spaces.forget(handle)
    }

    override suspend fun deleteSpace(handle: String) = runCatching {
        api.deleteSpace(handle)
        spaces.forget(handle)
    }.isSuccess

    override suspend fun spaceMembers(handle: String) = runCatching { api.spaceMembers(handle) }.getOrDefault(emptyList())

    override suspend fun setSpaceRole(handle: String, username: String, role: SpaceRole) =
        runCatching { api.setSpaceRole(handle, RoleChange(username, role)) }.isSuccess

    override suspend fun ban(handle: String, username: String) = runCatching { api.ban(handle, username) }.isSuccess

    override suspend fun addSpaceChannel(handle: String, title: String) =
        runCatching { spaces.remember(api.addSpaceChannel(handle, title)) }.isSuccess

    override suspend fun removeSpaceChannel(handle: String, channel: String) = runCatching {
        api.removeSpaceChannel(handle, channel)
        api.space(handle)?.let { spaces.remember(it) }
    }.isSuccess

    // Bots

    override val notices: SharedFlow<String> get() = bots.notices

    override fun isBot(conversationId: String) = bots.isBot(conversationId)

    override suspend fun commands(conversationId: String) = bots.commands(conversationId)

    override fun press(conversationId: String, messageId: String, data: String) {
        scope.launch {
            if (!bots.press(conversationId, bots.botMessageId(conversationId, messageId), data)) log.record(EventCode.SendFailed)
        }
    }

    // Calls

    override val callSignals: SharedFlow<CallSignal> = signals.asSharedFlow()

    /** Call messages skip the outbox: a ring that arrives minutes late is worse than none. */
    override suspend fun sendCallSignal(to: String, body: Body) {
        val content = Content(UUID.randomUUID().toString(), clock(), body)
        runCatching {
            if (!crypto.hasSessionSafe(to)) {
                val bundle = api.bundle(to) ?: return
                withContext(Dispatchers.IO) { crypto.startSession(bundle) }
                shareProfile(to)
            }
            val sealed = withContext(Dispatchers.IO) { crypto.encrypt(to, content) }
            api.send(to, OutgoingMessage(content.id, sealed.type, sealed.body))
        }.onFailure { log.record(EventCode.SendFailed) }
    }

    override suspend fun callToken(room: String): CallToken? = runCatching { api.callToken(room) }.getOrNull()

    override suspend fun voiceKey(groupId: String): String? = groups.voiceKey(groupId).also { flush() }

    // Outgoing

    /** Group conversations go through the sender key; everything else is one-to-one. */
    private suspend fun dispatch(conversationId: String, content: Content) {
        // «Избранное» stays on this phone: reactions, edits and timers there are local only.
        if (conversationId == me) return
        if (groups.groupOf(conversationId) != null) {
            if (!groups.send(conversationId, content) && content.body is Body.Text) {
                advanceStatus(listOf(content.id), SendStatus.Failed, force = true)
            } else if (content.body is Body.Text) {
                advanceStatus(listOf(content.id), SendStatus.Sent)
            }
            flush()
        } else {
            enqueue(conversationId, content)
        }
    }

    private suspend fun enqueueSealed(recipient: String, content: Content, type: Int, body: String) {
        val json = storageJson.encodeToString(Content.serializer(), content)
        db.outbox().insert(OutboxEntity(recipient = recipient, content = json, createdAt = clock(), sealedType = type, sealedBody = body))
    }

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
            val sealedType = item.sealedType
            val sealedBody = item.sealedBody
            if (sealedType != null && sealedBody != null) {
                api.send(item.recipient, OutgoingMessage("${content.id}:${item.recipient}", sealedType, sealedBody))
                db.outbox().delete(item.id)
                return true
            }
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
            ServerFrame.Synced -> {
                log.record(EventCode.ServerSynced)
                scope.launch { runCatching { spaces.sync() } }
            }
            is ServerFrame.SpacePost -> spaces.store(frame.post)
            is ServerFrame.SpacePostChanged -> frame.post?.let { spaces.store(it) } ?: spaces.removed(frame.space, frame.id)
        }
    }

    private suspend fun receive(envelope: Envelope) {
        if (envelope.type == EnvelopeType.BOT) {
            if (!bots.receive(envelope)) log.record(EventCode.DecryptFailed)
            socket.ack(envelope.serverId)
            return
        }
        val content = try {
            if (envelope.type == EnvelopeType.SENDERKEY) {
                groups.open(envelope.sender, envelope.body)
            } else {
                withContext(Dispatchers.IO) { crypto.decrypt(envelope) }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            log.record(EventCode.DecryptFailed)
            socket.ack(envelope.serverId)
            return
        }
        when {
            content == null -> log.record(EventCode.DecryptFailed)
            content.group != null -> applyGroup(envelope.sender, content)
            else -> apply(envelope.sender, content)
        }
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
            is Body.GroupUpdate -> groups.onUpdate(sender, body.state)
            is Body.SenderKey -> groups.onSenderKey(sender, body)
            is Body.JoinRequest -> groups.onJoinRequest(sender, body)
            is Body.GroupLeave -> groups.onLeave(sender, body.groupId)
            is Body.CallOffer, is Body.CallAnswer, is Body.CallEnd, is Body.Timer -> applyControl(sender, content, body)
            is Body.Profile -> {
                val known = db.contacts().get(sender)
                val updated = (known ?: ContactEntity(sender, sender)).copy(name = body.name.take(MAX_NAME), bio = body.bio.take(MAX_BIO))
                db.contacts().upsert(updated)
                db.conversations().get(sender)?.let { db.conversations().upsert(it.copy(title = body.name.ifBlank { sender })) }
            }
        }
    }

    /** A message inside a group or room, already checked to come from a member. */
    /** Calls and the disappearing-message timer: they change state, not the message list. */
    private suspend fun applyControl(sender: String, content: Content, body: Body) {
        if (body is Body.Timer) {
            if (db.conversations().get(sender) != null) db.conversations().setTimer(sender, body.seconds.coerceIn(0, MAX_TIMER_SECONDS))
        } else {
            signals.emit(CallSignal(sender, content.sentAtMillis, body))
        }
    }

    private suspend fun applyGroup(sender: String, content: Content) {
        val conversation = content.group ?: return
        val state = groups.groupOf(conversation) ?: return
        when (val body = content.body) {
            is Body.Text -> {
                if (!groups.canPost(state, conversation, sender)) return
                if (db.contacts().get(sender) == null) db.contacts().upsert(ContactEntity(sender, sender))
                storeIncoming(sender, conversation, content, body, author = contact(sender)?.name?.substringBefore(' ') ?: sender)
            }
            is Body.Reaction -> db.messages().get(body.target)?.takeIf { it.conversationId == conversation }?.let { message ->
                val entries = applyReaction(decodeReactions(message.reactions), sender, body.symbol, body.remove)
                db.messages().upsert(message.copy(reactions = encodeReactions(entries)))
            }
            is Body.Edit -> db.messages().get(body.target)?.takeIf { it.authorId == sender && it.conversationId == conversation }?.let {
                db.messages().upsert(it.copy(text = body.text, edited = true))
                refreshPreview(conversation)
            }
            is Body.Delete -> db.messages().get(body.target)?.takeIf {
                it.conversationId == conversation && (it.authorId == sender || state.isAdmin(sender))
            }?.let {
                db.messages().delete(it.id)
                refreshPreview(conversation)
            }
            is Body.Timer -> if (groups.canPost(state, conversation, sender)) {
                db.conversations().setTimer(conversation, body.seconds.coerceIn(0, MAX_TIMER_SECONDS))
            }
            else -> Unit
        }
    }

    private suspend fun storeIncoming(sender: String, conversation: String, content: Content, body: Body.Text, author: String?) {
        val placeholder = body.attachment?.let(Attachments::placeholder)
        val inserted = db.messages().insert(
            MessageEntity(
                id = content.id,
                conversationId = conversation,
                authorId = sender,
                text = body.text,
                sentAt = content.sentAtMillis,
                status = SendStatus.Delivered.name,
                replyToId = body.replyTo,
                attachment = encodeAttachment(placeholder),
                expiresAt = expiry(content.sentAtMillis, content.expiresInSeconds.coerceIn(0, MAX_TIMER_SECONDS)),
                forwardedFrom = if (body.forwarded) FORWARDED else null,
            ),
        )
        if (inserted == -1L) return
        db.conversations().incrementUnread(conversation)
        val preview = body.text.ifEmpty { labels(placeholder) }
        setPreview(conversation, author, preview, content.sentAtMillis)
        val row = db.conversations().get(conversation)
        val title = row?.parentId?.let { db.conversations().get(it)?.title } ?: row?.title
        incomingFlow.tryEmit(IncomingNote(conversation, title ?: sender, author?.let { contactCache.value[it]?.name ?: it }, preview))
        body.attachment?.let { pointer ->
            scope.launch {
                val local = runCatching { withContext(Dispatchers.IO) { Attachments.download(api, media, pointer) } }.getOrNull()
                val stored = db.messages().get(content.id)
                if (local != null && stored != null) db.messages().upsert(stored.copy(attachment = encodeAttachment(local)))
            }
        }
    }

    private suspend fun receiveText(sender: String, content: Content, body: Body.Text) {
        if (db.contacts().get(sender) == null) db.contacts().upsert(ContactEntity(sender, sender))
        ensureConversation(sender)
        showTyping(sender, false)
        val known = db.messages().get(content.id) != null
        storeIncoming(sender, sender, content, body, author = null)
        if (!known) enqueue(sender, Content(UUID.randomUUID().toString(), clock(), Body.Receipt(ReceiptKind.Delivered, listOf(content.id))))
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
        if (db.conversations().get(username) != null || groups.groupOf(username) != null) return
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
        val row = db.conversations().get(conversationId)
        if (row == null) ensureConversation(conversationId)
        db.conversations().setPreview(conversationId, author, text, at)
        row?.parentId?.let { db.conversations().setPreview(it, author, text, at) }
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
        const val SEARCH_LIMIT = 100
        const val SIGNAL_BUFFER = 16
        const val FORWARDED = "forwarded"
        const val MILLIS_PER_SECOND = 1000L
        const val SWEEP_MIN_MILLIS = 1_000L
        const val SWEEP_MAX_MILLIS = 60_000L

        /** Longest timer accepted from others: a year. */
        const val MAX_TIMER_SECONDS = 365 * 24 * 3600
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
