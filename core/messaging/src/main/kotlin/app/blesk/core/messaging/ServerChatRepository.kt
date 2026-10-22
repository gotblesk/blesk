package app.blesk.core.messaging

import androidx.room.withTransaction
import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.data.BotAccounts
import app.blesk.core.data.BotController
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.Connection
import app.blesk.core.data.GroupController
import app.blesk.core.data.IncomingMessages
import app.blesk.core.data.KeyVerification
import app.blesk.core.data.SpaceController
import app.blesk.core.data.SpaceResult
import app.blesk.core.data.Transfer
import app.blesk.core.data.calls.CallSignal
import app.blesk.core.data.calls.CallSignaling
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.ContactEntity
import app.blesk.core.data.db.ConversationEntity
import app.blesk.core.data.db.MessageEntity
import app.blesk.core.data.db.PIN_SEPARATOR
import app.blesk.core.data.db.pinnedIds
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Presence
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.model.StoredMedia
import app.blesk.core.data.model.files
import app.blesk.core.data.model.splitLongText
import app.blesk.core.data.model.withoutFiles
import app.blesk.core.data.settings.Audience
import app.blesk.core.data.settings.AutoKind
import app.blesk.core.data.settings.IncomingNote
import app.blesk.core.data.settings.NoteKind
import app.blesk.core.data.settings.Privacy
import app.blesk.core.media.MediaVault
import app.blesk.core.network.ApiException
import app.blesk.core.network.BleskApi
import app.blesk.core.network.Link
import app.blesk.core.network.Transport
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.CallToken
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.CreateSpace
import app.blesk.shared.protocol.Envelope
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.InviteLink
import app.blesk.shared.protocol.LinkPreview
import app.blesk.shared.protocol.OutgoingMessage
import app.blesk.shared.protocol.Post
import app.blesk.shared.protocol.ReceiptKind
import app.blesk.shared.protocol.Right
import app.blesk.shared.protocol.RoleChange
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceRole
import app.blesk.shared.protocol.Spaces
import app.blesk.shared.protocol.TextSafety
import app.blesk.shared.protocol.UpdateSpace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    private val profile: () -> OwnProfile,
    private val labels: (Attachment?) -> String,
    private val log: EventLog,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The title of «Избранное», your notes to yourself; it lives under your own username. */
    private val savedTitle: String = "",
    /** Whether the phone has any network; the connection status tells it apart from a silent server. */
    private val network: Flow<Boolean> = flowOf(true),
    /** «Не возвращать из архива»: archived chats stay there whatever arrives. */
    private val keepArchived: () -> Boolean = { false },
    /** Builds the card of the first link in a message; asked only when link previews are on. */
    private val linkPreview: suspend (text: String) -> LinkPreview? = { null },
    /** «Автозагрузка»: whether a kind of file comes by itself over the network the phone is on now. */
    private val autoDownload: (AutoKind) -> Boolean = { true },
    /** How long the outbox waits after a failed pass when the server names no time of its own. */
    retryMillis: Long = Outbox.RETRY_MILLIS,
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
    private val changes = MessageChanges(db, media, labels)
    private val transferState = Transfers()
    override val transfers: StateFlow<Map<String, Transfer>> = transferState.progress
    private val downloads = Downloads(db, api, media, transferState, scope, autoDownload)
    private val uploads = ConcurrentHashMap<String, Job>()
    private val profiles = ProfileExchange(
        db = db,
        api = api,
        crypto = crypto,
        media = media,
        me = me,
        profile = profile,
        photoAudience = { privacy().photo },
        allowed = ::allowed,
        enqueue = ::enqueue,
    )
    private val outbox: Outbox = Outbox(
        db = db,
        api = api,
        crypto = crypto,
        accounts = accounts,
        me = me,
        scope = scope,
        log = log,
        clock = clock,
        retryMillis = retryMillis,
        shareProfile = profiles::share,
        linkOf = ::linkOf,
    )
    private val bots =
        BotEngine(db, api, me, clock) { bot, title, text ->
            scope.launch { returnFromArchive(bot) }
            incomingFlow.tryEmit(IncomingNote(bot, title, null, text))
        }
    private val groups = GroupEngine(
        db = db,
        crypto = groupCrypto,
        me = me,
        outbox = outbox,
        clock = clock,
        labels = labels,
        purged = { rows ->
            rows.forEach {
                downloads.cancel(it.id)
                media.forget(decodeAttachment(it.attachment))
            }
        },
    )
    private val signals = MutableSharedFlow<CallSignal>(extraBufferCapacity = SIGNAL_BUFFER)
    private val typingState = MutableStateFlow<Set<String>>(emptySet())
    private val typingJobs = HashMap<String, Job>()
    private val contactCache = db.contacts().observeAll()
        .map { list -> list.associate { it.username to it.toModel() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override val typing: StateFlow<Set<String>> = typingState.asStateFlow()

    override val conversations: Flow<List<Conversation>> =
        combine(
            db.conversations().observeAll(),
            db.groups().observeAll(),
            spaces.spaces,
            db.contacts().observeAll(),
        ) { list, groupRows, spaceInfo, people ->
            val states = groupRows.associate { it.id to storageJson.decodeFromString(GroupState.serializer(), it.state) }
            val byName = people.associateBy { it.username }
            list.filter { it.previewText != null && it.parentId == null && byName[it.peer]?.blocked != true }.map { row ->
                val space = spaces.handleOf(row.id)?.let(spaceInfo::get)
                val model = if (space != null) row.toModel(space) else row.toModel(states[row.id], me)
                val peer = row.peer?.let(byName::get)
                model.copy(request = model.request || peer?.added == false, photo = peer?.photo)
            }
        }

    override fun accept(username: String) {
        scope.launch { db.contacts().markAdded(username) }
    }

    override fun removeContact(username: String) {
        scope.launch { db.contacts().markRemoved(username) }
    }

    /** A stranger whose request you have not accepted: they get no receipts and no typing from you. */
    private suspend fun isRequest(username: String): Boolean = db.contacts().get(username)?.added == false

    override val contacts: Flow<List<Contact>> =
        db.contacts().observeAll().map { list -> list.filter { it.added && !it.blocked }.map { it.toModel() } }

    override val blocked: Flow<List<Contact>> = db.contacts().observeAll().map { list -> list.filter { it.blocked }.map { it.toModel() } }

    override fun setBlocked(username: String, blocked: Boolean) {
        scope.launch {
            db.contacts().insertIfAbsent(ContactEntity(username, username, added = false))
            db.contacts().setBlocked(username, blocked)
        }
    }

    /**
     * Whether [sender] may reach you for something open to [audience]. Decided here, on your
     * phone: the server relays everything and knows neither your contacts nor whom you block.
     */
    private suspend fun allowed(sender: String, audience: Audience): Boolean {
        val contact = db.contacts().get(sender)
        if (contact?.blocked == true) return false
        return when (audience) {
            Audience.Everyone -> true
            Audience.Contacts -> contact?.added == true
            Audience.Nobody -> false
        }
    }

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
                else -> row.toModel(state?.takeIf { row.parentId == null }, me)
            }
        }

    override fun messages(conversationId: String): Flow<List<Message>> =
        db.messages().observe(conversationId).map { list -> list.map { it.toModel(me) } }

    override val music: Flow<List<Message>>
        get() = db.messages().observeMusic().map { list -> list.map { it.toModel(me) }.filter { it.attachment is Attachment.Music } }

    override fun contact(id: String): Contact? = contactCache.value[id]

    /** Full-text search over the decrypted database on this phone; every word is matched as a prefix. */
    override suspend fun search(query: String): List<Message> {
        val words = query.split(Regex("\\s+")).map { word -> word.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val match = words.joinToString(" ") { "$it*" }
        return runCatching { db.messages().searchFullText(match) }.getOrDefault(emptyList())
            .take(SEARCH_LIMIT)
            .map { it.toModel(me) }
    }

    fun start() {
        scope.launch {
            // The token lives in the database, which Room keeps off the main thread the app runs this on.
            withContext(Dispatchers.IO) { if (!accounts.restore(api)) runCatching { accounts.signIn(api, me) } }
            socket.start()
        }
        scope.launch { socket.frames.collect { frame -> handle(frame) } }
        scope.launch { socket.connected.filter { it }.collect { outbox.flush() } }
        scope.launch { socket.connected.filter { it }.collect { presenceTracker.reconnected() } }
        // The server only keeps whether you can be found by name; it is told again on every connection.
        scope.launch { socket.connected.filter { it }.collect { setDiscoverable(privacy().findable) } }
        scope.launch { socket.connected.filter { !it }.collect { synced.value = false } }
        // Without this the next attempt could be a minute away after the network comes back.
        scope.launch { network.drop(1).filter { it }.collect { socket.retryNow() } }
        scope.launch { sweep() }
        scope.launch { ensureSaved() }
        scope.launch { groups.tidy().forEach { applyGroup(it.sender, it.content) } }
    }

    fun stop() = socket.stop()

    override suspend fun openDirect(username: String, link: String?): String? {
        if (username == me) return null
        if (bots.isBot(username)) return bots.open(username)
        val existing = db.contacts().get(username)
        existing?.let { db.contacts().markAdded(username) }
        val fresh = db.conversations().get(username) == null
        if (existing == null) {
            val account = runCatching { api.account(username, link) }.getOrNull() ?: return null
            val key = account.identityKey
            // Their profile or first message may have brought them in while the server answered; that row keeps what it has.
            if (db.contacts().insertIfAbsent(ContactEntity(account.username, account.username, identityKey = key)) == -1L) {
                db.contacts().markAddedWithKey(account.username, key)
            }
        }
        ensureConversation(username)
        // A chat you start takes your default for disappearing messages; one someone else starts keeps theirs.
        val timer = privacy().defaultTimerSeconds
        if (fresh && timer > 0) setTimer(username, timer)
        return username
    }

    override suspend fun linkKey(): String = Api.linkKey(Base64.getDecoder().decode(keys.identityPublicKey()))

    override suspend fun deleteAccount(): Boolean = runCatching { api.deleteAccount() }.isSuccess

    override suspend fun setDiscoverable(discoverable: Boolean) {
        runCatching { api.setDiscoverable(discoverable) }.onFailure { log.record(EventCode.SendFailed) }
    }

    /** The link key of someone you already talk to, from the identity key you hold, so their keys stay reachable if they hide. */
    private suspend fun linkOf(username: String): String? =
        db.contacts().get(username)?.identityKey?.let { key -> runCatching { Api.linkKey(Base64.getDecoder().decode(key)) }.getOrNull() }

    override fun send(conversationId: String, text: String, replyToId: String?) {
        val parts = splitLongText(text)
        if (parts.isEmpty()) return
        // One coroutine for all parts, so they arrive in the order they were written.
        scope.launch {
            parts.forEachIndexed { index, body ->
                val reply = replyToId.takeIf { index == 0 }
                when {
                    bots.isBot(conversationId) -> bots.send(conversationId, body, reply)
                    spaces.handleOf(conversationId) != null ->
                        runCatching { spaces.send(conversationId, body, reply, null) }.onFailure { log.record(EventCode.SendFailed) }
                    else -> post(conversationId, body, reply, attachment = null, forwarded = false)
                }
            }
        }
    }

    override fun sendAttachment(conversationId: String, attachment: Attachment, caption: String, replyToId: String?) {
        // Bots take text only; files to a program in the clear are not worth the risk.
        if (bots.isBot(conversationId)) return
        if (spaces.handleOf(conversationId) != null) {
            // Open rooms keep one file per post, so an album goes out as a row of posts.
            val parts = (attachment as? Attachment.Album)?.items ?: listOf(attachment)
            scope.launch {
                parts.forEachIndexed { index, part ->
                    val text = caption.takeIf { index == parts.lastIndex }.orEmpty()
                    runCatching { spaces.send(conversationId, text, replyToId, part) }.onFailure { log.record(EventCode.SendFailed) }
                }
            }
            return
        }
        scope.launch { post(conversationId, caption.trim(), replyToId, attachment, forwarded = false) }
    }

    private val synced = MutableStateFlow(false)

    override val connection: Flow<Connection> = combine(network, socket.link, synced) { hasNetwork, link, caughtUp ->
        when {
            !hasNetwork -> Connection.NoNetwork
            link == Link.Failing -> Connection.Unreachable
            link == Link.Connecting -> Connection.Connecting
            !caughtUp -> Connection.Updating
            else -> Connection.Online
        }
    }.distinctUntilChanged()

    override fun reconnect() = socket.retryNow()

    override fun retry(conversationId: String, messageId: String) {
        scope.launch {
            val failed = db.messages().get(messageId)
                ?.takeIf { it.conversationId == conversationId && it.authorId == me && it.status == SendStatus.Failed.name }
                ?: return@launch
            db.messages().delete(messageId)
            val attachment = decodeAttachment(failed.attachment)
            if (bots.isBot(conversationId)) {
                bots.send(conversationId, failed.text, failed.replyToId)
            } else {
                post(conversationId, failed.text, failed.replyToId, attachment, forwarded = failed.forwardedFrom != null, failed.link)
            }
        }
    }

    override fun forward(conversationId: String, messageId: String, to: List<String>) {
        scope.launch {
            val original = db.messages().get(messageId)?.takeIf { it.conversationId == conversationId } ?: return@launch
            val attachment = decodeAttachment(original.attachment)
            to.distinct().forEach { target ->
                when {
                    bots.isBot(target) -> if (original.text.isNotBlank()) bots.send(target, original.text, null)
                    spaces.handleOf(target) != null -> runCatching { spaces.send(target, original.text, null, attachment) }
                    else -> post(target, original.text, null, attachment, forwarded = true, original.link)
                }
            }
        }
    }

    /** Stores, then encrypts and sends one of your messages; with the chat's timer if it has one. */
    private suspend fun post(
        conversationId: String,
        text: String,
        replyToId: String?,
        attachment: Attachment?,
        forwarded: Boolean,
        storedLink: String? = null,
    ) {
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
                link = storedLink,
            ),
        )
        setPreview(conversationId, null, text.ifEmpty { labels(attachment) }, sentAt)
        val link = storedLink?.let(::decodeLink) ?: cardFor(id, text, attachment, forwarded)
        if (conversationId == me) {
            advanceStatus(listOf(id), SendStatus.Read)
            return
        }
        val pointers = if (attachment == null) {
            emptyList()
        } else {
            uploads[id] = currentCoroutineContext().job
            try {
                withContext(Dispatchers.IO) { Attachments.uploadEach(api, attachment, transferState, id) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } finally {
                uploads.remove(id)
            }
        }
        if (pointers == null) {
            advanceStatus(listOf(id), SendStatus.Failed, force = true)
            log.record(EventCode.SendFailed)
            return
        }
        val album = attachment is Attachment.Album
        val body = Body.Text(
            text = text,
            replyTo = replyToId,
            attachment = pointers.firstOrNull().takeUnless { album },
            forwarded = forwarded,
            link = link,
            album = if (album) pointers else emptyList(),
        )
        dispatch(conversationId, Content(id, sentAt, body, expiresInSeconds = timer))
        log.record(EventCode.MessageQueued)
    }

    override suspend fun storedMedia(): List<StoredMedia> = db.messages().withFiles().mapNotNull { row ->
        decodeAttachment(row.attachment)?.takeIf { it.files.isNotEmpty() }?.let { StoredMedia(row.conversationId, row.sentAt, it) }
    }

    override suspend fun clearMedia(conversationId: String?, beforeMillis: Long, keep: Set<String>) {
        db.messages().withFiles()
            .filter { (conversationId == null || it.conversationId == conversationId) && it.sentAt < beforeMillis && it.id !in keep }
            .forEach { row ->
                val attachment = decodeAttachment(row.attachment) ?: return@forEach
                val cleared = attachment.withoutFiles() ?: return@forEach
                media.forget(attachment)
                db.messages().setAttachment(row.id, encodeAttachment(cleared))
            }
    }

    /** Channels tell about every post; rooms only about the ones that mention you. */
    private suspend fun announcePost(conversation: String, post: Post) {
        if (post.author == me) return
        val room = spaces.spaces.value[post.space]?.kind == SpaceKind.Room
        if (room && !mentionsMe(post.text)) return
        val row = db.conversations().get(conversation)
        val title = row?.parentId?.let { db.conversations().get(it)?.title } ?: row?.title ?: post.space
        val preview = post.text.ifEmpty { labels(null) }
        val kind = if (room) NoteKind.Room else NoteKind.Channel
        incomingFlow.tryEmit(IncomingNote(conversation, title, post.author.takeIf { room }, preview, kind))
    }

    private fun mentionsMe(text: String) = Regex("""(^|[^\w@])@${Regex.escape(me)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)

    /** The link card for a new message of yours, kept with it; the bubble shows it as soon as it is ready. */
    private suspend fun cardFor(id: String, text: String, attachment: Attachment?, forwarded: Boolean): LinkPreview? {
        if (attachment != null || forwarded || !privacy().linkPreviews) return null
        val card = withTimeoutOrNull(LINK_PREVIEW_MILLIS) { linkPreview(text) } ?: return null
        db.messages().setLink(id, encodeLink(card))
        return card
    }

    override fun cancelTransfer(conversationId: String, messageId: String) {
        if (downloads.cancel(messageId)) return
        // An upload that is stopped takes its unsent message with it.
        uploads.remove(messageId)?.cancel() ?: return
        scope.launch {
            db.messages().get(messageId)?.takeIf { it.conversationId == conversationId }?.let { stored ->
                db.messages().delete(stored.id)
                media.forget(decodeAttachment(stored.attachment))
            }
            refreshPreview(conversationId)
        }
    }

    override fun retryDownload(conversationId: String, messageId: String) = downloads.start(messageId)

    override suspend fun stream(conversationId: String, messageId: String, index: Int) = downloads.stream(messageId, index)

    override fun setArchived(conversationId: String, archived: Boolean) {
        scope.launch { db.conversations().setArchived(conversationId, archived) }
    }

    override fun deleteChat(conversationId: String, forBoth: Boolean) {
        scope.launch {
            val direct = groups.groupOf(conversationId) == null && !isSpace(conversationId) && !bots.isBot(conversationId)
            if (forBoth && direct) dispatch(conversationId, Content(UUID.randomUUID().toString(), clock(), Body.ClearChat))
            changes.clear(conversationId)
        }
    }

    override fun mute(conversationId: String, untilMillis: Long) {
        scope.launch { db.conversations().setMuted(conversationId, untilMillis) }
    }

    override fun markUnread(conversationId: String, unread: Boolean) {
        scope.launch { db.conversations().setMarkedUnread(conversationId, unread) }
    }

    /** «Избранное» is a chat with yourself: always there, pinned, and never sent anywhere. */
    private suspend fun ensureSaved() {
        if (savedTitle.isEmpty()) return
        db.conversations().insertIfAbsent(
            ConversationEntity(
                id = me,
                kind = ConversationKind.Saved.name,
                title = savedTitle,
                protection = Protection.EndToEnd.name,
                peer = null,
                updatedAt = clock(),
                pinned = true,
                pinRank = 0,
                previewText = "",
            ),
        )
    }

    override fun saveDraft(conversationId: String, text: String) {
        scope.launch { db.conversations().setDraft(conversationId, text.take(Bots.MAX_TEXT)) }
    }

    override fun setChatPinned(conversationId: String, pinned: Boolean) {
        scope.launch {
            val rank = if (pinned) db.conversations().lastPinRank() + 1 else 0
            db.conversations().setPinned(conversationId, pinned, rank)
        }
    }

    override fun reorderPinned(ids: List<String>) {
        scope.launch { db.conversations().reorderPinned(ids) }
    }

    private suspend fun returnFromArchive(conversationId: String) {
        if (!keepArchived()) db.conversations().returnFromArchive(conversationId, clock())
    }

    override fun pinMessage(conversationId: String, messageId: String, pinned: Boolean) {
        scope.launch {
            val current = db.conversations().get(conversationId)?.pinnedIds().orEmpty()
            val next = if (pinned) (current - messageId) + messageId else current - messageId
            db.conversations().pinMessage(conversationId, next.joinToString(PIN_SEPARATOR).ifEmpty { null })
        }
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
            val mine = decodeReactions(message.reactions).any { it.symbol == symbol && me in it.users }
            db.messages().changeReactions(messageId) { encodeReactions(applyReaction(decodeReactions(it), me, symbol, remove = mine)) }
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
            if (db.messages().editText(messageId, me, body) == 0) return@launch
            refreshPreview(conversationId)
            dispatch(conversationId, Content(UUID.randomUUID().toString(), clock(), Body.Edit(messageId, body)))
        }
    }

    override fun delete(conversationId: String, messageId: String, forEveryone: Boolean) {
        if (!forEveryone) {
            scope.launch {
                db.messages().delete(messageId)
                refreshPreview(conversationId)
            }
            return
        }
        if (spaces.handleOf(conversationId) != null) {
            scope.launch { runCatching { spaces.delete(messageId) } }
            return
        }
        scope.launch {
            val message = db.messages().get(messageId) ?: return@launch
            val moderator = groups.groupOf(conversationId)?.let { GroupPolicy.can(it, me, Right.DeleteOthers) } == true
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
            if (privacy().readReceipts && direct && !isRequest(conversationId)) {
                enqueue(conversationId, Content(UUID.randomUUID().toString(), clock(), Body.Receipt(ReceiptKind.Read, ids)))
            }
        }
    }

    override fun setTyping(conversationId: String, active: Boolean) {
        if (!privacy().typingIndicator) return
        scope.launch {
            if (groups.groupOf(conversationId) != null || isRequest(conversationId)) return@launch
            sendEphemeral(conversationId, Body.Typing(active))
        }
    }

    /** Something that only matters right now: never queued, never starts a session, lost if they are offline. */
    private suspend fun sendEphemeral(to: String, body: Body) {
        if (!crypto.hasSessionSafe(to)) return
        runCatching {
            val content = Content(UUID.randomUUID().toString(), clock(), body)
            val sealed = withContext(Dispatchers.IO) { crypto.encrypt(to, content) }
            api.send(to, OutgoingMessage(content.id, sealed.type, sealed.body, ephemeral = true))
        }
    }

    // Presence

    private val presenceTracker = PresenceTracker(
        scope = scope,
        clock = clock,
        audience = { privacy().online },
        peers = {
            db.conversations().recentDirect(PresenceTracker.PEERS).filter { it != me && !bots.isBot(it) }
        },
        allowed = ::allowed,
        send = ::sendEphemeral,
    )
    override val presence: StateFlow<Map<String, Presence>> = presenceTracker.presence

    override fun setForeground(foreground: Boolean) = presenceTracker.setForeground(foreground)

    // Groups and rooms

    override fun group(id: String): Flow<GroupState?> = db.groups().observeAll().map { rows ->
        rows.firstOrNull { it.id == id }?.let { storageJson.decodeFromString(GroupState.serializer(), it.state) }
    }

    // The engine sends what a change queued as soon as the change is stored.

    override suspend fun createGroup(kind: GroupKind, title: String, members: List<String>): String =
        groups.create(kind, TextSafety.clean(title), members)

    override suspend fun rename(groupId: String, title: String) = groups.rename(groupId, TextSafety.clean(title))

    override suspend fun addMembers(groupId: String, usernames: List<String>) = groups.addMembers(groupId, usernames)

    override suspend fun removeMember(groupId: String, username: String) = groups.remove(groupId, username)

    override suspend fun setRole(groupId: String, username: String, role: GroupRole) = groups.setRole(groupId, username, role)

    override suspend fun addChannel(groupId: String, title: String, kind: GroupChannelKind, adminsOnly: Boolean) =
        groups.addChannel(groupId, TextSafety.clean(title), kind, adminsOnly)

    override suspend fun removeChannel(groupId: String, channelId: String) = groups.removeChannel(groupId, channelId)

    override suspend fun change(groupId: String, change: GroupChange) = groups.change(groupId, change)

    override suspend fun setInvite(groupId: String, enabled: Boolean): String? =
        groups.setInvite(groupId, enabled)?.let { InviteLink.format(groupId, me, it) }

    override suspend fun leave(groupId: String) = groups.leave(groupId)

    override suspend fun join(link: String): Boolean {
        val invite = InviteLink.parse(link) ?: return false
        if (openDirect(invite.admin) == null) return false
        groups.joining(invite.groupId)
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

    override suspend fun spaceMembers(handle: String) = runCatching { api.spaceMembers(handle) }.getOrNull()

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
                val bundle = api.bundle(to, linkOf(to)) ?: return
                withContext(Dispatchers.IO) { crypto.startSession(bundle) }
                profiles.share(to)
            }
            val sealed = withContext(Dispatchers.IO) { crypto.encrypt(to, content) }
            api.send(to, OutgoingMessage(content.id, sealed.type, sealed.body))
        }.onFailure { log.record(EventCode.SendFailed) }
    }

    override suspend fun callToken(room: String): CallToken? = runCatching { api.callToken(room) }.getOrNull()

    override suspend fun voiceKey(groupId: String): String? = groups.voiceKey(groupId)

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
        } else {
            // Writing to someone makes them a contact, even if they wrote first.
            if (content.body is Body.Text) db.contacts().markAdded(conversationId)
            enqueue(conversationId, content)
        }
    }

    private suspend fun enqueue(recipient: String, content: Content) = outbox.enqueue(recipient, content)

    // Incoming

    private suspend fun handle(frame: ServerFrame) {
        when (frame) {
            is ServerFrame.Deliver -> receive(frame.envelope)
            is ServerFrame.KeysLow -> refillKeys(frame.preKeys, frame.kyberPreKeys)
            ServerFrame.Synced -> {
                synced.value = true
                log.record(EventCode.ServerSynced)
                scope.launch { runCatching { spaces.sync() } }
            }
            is ServerFrame.SpacePost -> spaces.store(frame.post)?.let { announcePost(it, frame.post) }
            // Not an elvis: store gives null for a post already here, as an edited one always is, and that is no removal.
            is ServerFrame.SpacePostChanged -> {
                val post = frame.post
                if (post != null) spaces.store(post) else spaces.removed(frame.space, frame.id)
            }
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
        if (!admits(sender, content.body)) return
        when (val body = content.body) {
            is Body.Text -> receiveText(sender, content, body)
            is Body.Receipt -> advanceStatus(
                body.ids,
                if (body.kind == ReceiptKind.Read) SendStatus.Read else SendStatus.Delivered,
            )
            is Body.Typing -> showTyping(sender, body.active)
            is Body.Reaction, is Body.Edit, is Body.Delete, is Body.ClearChat -> changes.apply(sender, body)
            is Body.GroupUpdate -> takeUpdate(sender, body.state)
            is Body.SenderKey -> groups.onSenderKey(sender, body)
            is Body.JoinRequest -> groups.onJoinRequest(sender, body)
            is Body.GroupLeave -> groups.onLeave(sender, body.groupId)
            is Body.CallOffer, is Body.CallAnswer, is Body.CallEnd, is Body.Timer -> applyControl(sender, content, body)
            is Body.Profile -> profiles.receive(sender, body)
            is Body.Presence -> presenceTracker.receive(sender, body)
        }
    }

    override fun profileChanged() {
        scope.launch { profiles.broadcast(clock) }
    }

    /** Privacy and blocking on what arrives directly; refused things vanish as if never sent. */
    private suspend fun admits(sender: String, body: Body): Boolean {
        val privacy = privacy()
        return when (body) {
            is Body.CallOffer -> allowed(sender, privacy.calls)
            // Joining a new group needs permission; updates of a group you are in only need you not to block the admin.
            is Body.GroupUpdate -> allowed(sender, if (groups.state(body.state.id) == null) privacy.groups else Audience.Everyone)
            is Body.Text, is Body.Reaction, is Body.Edit, is Body.Delete, is Body.ClearChat, is Body.Typing, is Body.Timer ->
                allowed(sender, privacy.write)
            else -> allowed(sender, Audience.Everyone)
        }
    }

    /** Calls and the disappearing-message timer: they change state, not the message list. */
    private suspend fun applyControl(sender: String, content: Content, body: Body) {
        if (body is Body.Timer) {
            if (db.conversations().get(sender) != null) db.conversations().setTimer(sender, body.seconds.coerceIn(0, MAX_TIMER_SECONDS))
        } else {
            signals.emit(CallSignal(sender, content.sentAtMillis, body))
        }
    }

    /** A state of a group from a member; the messages that waited for it are judged again once it is taken. */
    private suspend fun takeUpdate(sender: String, state: GroupState) {
        when (groups.onUpdate(sender, state)) {
            UpdateOutcome.Taken -> groups.released(state.id).forEach { applyGroup(it.sender, it.content) }
            UpdateOutcome.Refused -> log.record(EventCode.GroupUpdateRefused)
            UpdateOutcome.Ignored -> Unit
        }
    }

    /** A message inside a group or room, already checked to come from a member. */
    private suspend fun applyGroup(sender: String, content: Content) {
        val conversation = content.group ?: return
        val state = groups.groupOf(conversation) ?: return
        if (!admitted(state, conversation, sender, content)) return
        when (val body = content.body) {
            is Body.Text -> {
                db.contacts().insertIfAbsent(ContactEntity(sender, sender))
                storeIncoming(sender, conversation, content, body, author = contact(sender)?.name?.substringBefore(' ') ?: sender)
            }
            is Body.Reaction -> if (db.messages().get(body.target)?.conversationId == conversation) {
                db.messages().changeReactions(body.target) {
                    encodeReactions(applyReaction(decodeReactions(it), sender, body.symbol, body.remove))
                }
            }
            is Body.Edit -> if (db.messages().get(body.target)?.conversationId == conversation) {
                if (db.messages().editText(body.target, sender, body.text) > 0) refreshPreview(conversation)
            }
            is Body.Delete -> db.messages().get(body.target)?.takeIf {
                it.conversationId == conversation && (it.authorId == sender || GroupPolicy.can(state, sender, Right.DeleteOthers))
            }?.let {
                db.messages().delete(it.id)
                refreshPreview(conversation)
            }
            is Body.Timer -> db.conversations().setTimer(conversation, body.seconds.coerceIn(0, MAX_TIMER_SECONDS))
            else -> Unit
        }
    }

    /**
     * Whether this phone keeps a group message: nothing of a conversation it may not read is stored, counted, shown,
     * announced or downloaded. One that a newer state may let it read waits for that state.
     */
    private suspend fun admitted(state: GroupState, conversation: String, sender: String, content: Content): Boolean =
        when (groups.admission(state, conversation, sender, content)) {
            Admission.Store -> true
            Admission.Hold -> {
                groups.hold(sender, content)
                log.record(EventCode.GroupMessageHeld)
                false
            }
            Admission.Drop -> {
                log.record(EventCode.GroupMessageDropped)
                false
            }
        }

    private suspend fun storeIncoming(sender: String, conversation: String, content: Content, body: Body.Text, author: String?) {
        val album = body.album.take(Attachment.Album.MAX)
        val remote = RemoteFiles(body.attachment?.takeIf { album.isEmpty() && it.kind != Attachments.KIND_THEME }, album)
            .takeIf { it.single != null || it.album.isNotEmpty() }
        val placeholder = if (album.isNotEmpty()) Attachments.placeholders(album) else body.attachment?.let(Attachments::placeholder)
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
                link = encodeLink(body.link?.sanitized(body.text)),
                remote = remote?.encode(),
            ),
        )
        if (inserted == -1L) return
        db.conversations().incrementUnread(conversation)
        returnFromArchive(conversation)
        val preview = body.text.ifEmpty { labels(placeholder) }
        setPreview(conversation, author, preview, content.sentAtMillis)
        val row = db.conversations().get(conversation)
        val title = row?.parentId?.let { db.conversations().get(it)?.title } ?: row?.title
        val kind = when {
            row?.parentId != null -> NoteKind.Room
            author != null -> NoteKind.Group
            else -> NoteKind.Direct
        }
        if (kind != NoteKind.Room || mentionsMe(body.text)) {
            val name = author?.let { contactCache.value[it]?.name ?: it }
            incomingFlow.tryEmit(IncomingNote(conversation, title ?: sender, name, preview, kind))
        }
        remote?.let { downloads.arrived(content.id, it) }
    }

    private suspend fun receiveText(sender: String, content: Content, body: Body.Text) {
        db.contacts().insertIfAbsent(ContactEntity(sender, sender, added = false))
        ensureConversation(sender)
        showTyping(sender, false)
        val known = db.messages().get(content.id) != null
        storeIncoming(sender, sender, content, body, author = null)
        if (!known && !isRequest(sender)) {
            enqueue(sender, Content(UUID.randomUUID().toString(), clock(), Body.Receipt(ReceiptKind.Delivered, listOf(content.id))))
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
        if (db.conversations().get(username) != null || groups.groupOf(username) != null) return
        // With the name read in the same transaction, a profile stored meanwhile either comes first and is read
        // here, or comes after and renames the chat this creates.
        db.withTransaction {
            val name = db.contacts().get(username)?.name?.ifBlank { null } ?: username
            db.conversations().insertIfAbsent(
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
    }

    private suspend fun setPreview(conversationId: String, author: String?, text: String, at: Long) {
        val row = db.conversations().get(conversationId)
        if (row == null) ensureConversation(conversationId)
        db.conversations().setPreview(conversationId, author, text, at)
        row?.parentId?.let { db.conversations().setPreview(it, author, text, at) }
    }

    private suspend fun refreshPreview(conversationId: String) = changes.refreshPreview(conversationId)

    /** Statuses only move forward, except an explicit failure; the database decides, so a late Sent cannot undo a receipt. */
    private suspend fun advanceStatus(ids: List<String>, status: SendStatus, force: Boolean = false) {
        if (force) db.messages().setStatus(ids, me, status.name) else db.messages().advanceStatus(ids, me, status)
    }

    private suspend fun MessageCrypto.hasSessionSafe(username: String) = withContext(Dispatchers.IO) { hasSession(username) }

    private companion object {
        const val TYPING_TIMEOUT_MILLIS = 6_000L
        const val SEARCH_LIMIT = 100
        const val SIGNAL_BUFFER = 16
        const val FORWARDED = "forwarded"
        const val LINK_PREVIEW_MILLIS = 8_000L
        const val MILLIS_PER_SECOND = 1000L
        const val SWEEP_MIN_MILLIS = 1_000L
        const val SWEEP_MAX_MILLIS = 60_000L

        /** Longest timer accepted from others: a year. */
        const val MAX_TIMER_SECONDS = 365 * 24 * 3600
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
