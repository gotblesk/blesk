package app.blesk.core.messaging

import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.OutboxEntity
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.SendStatus
import app.blesk.core.network.ApiException
import app.blesk.core.network.BleskApi
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.OutgoingMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The encrypted outbox: content waits here, in the order it was written, until it is encrypted and the
 * server has taken it. A bad moment of the network or the server keeps it for a later pass; only a refusal
 * of the item itself takes it off the queue as failed.
 */
internal class Outbox(
    private val db: BleskDatabase,
    private val api: BleskApi,
    private val crypto: MessageCrypto,
    private val accounts: AccountManager,
    private val me: String,
    private val scope: CoroutineScope,
    private val log: EventLog,
    private val clock: () -> Long,
    /** How long to wait after a failed pass when the server names no time of its own. */
    private val retryMillis: Long,
    /** Your profile, sent straight away ahead of the first message of a new session. */
    private val shareProfile: suspend (recipient: String) -> Unit,
    /** The link key of someone you already talk to, so their keys stay reachable if they hide. */
    private val linkOf: suspend (username: String) -> String?,
) : GroupOutbox {
    private val lock = Mutex()

    /** Someone wants a pass: set before trying [lock], cleared by the pass that takes it, before it reads the queue. */
    private val asked = AtomicBoolean(false)
    private val timerLock = Any()

    /** The pending retry, if any; guarded by [timerLock]. */
    private var timer: Job? = null

    override suspend fun queue(recipient: String, content: Content) {
        val json = storageJson.encodeToString(Content.serializer(), content)
        db.outbox().insert(OutboxEntity(recipient = recipient, content = json, createdAt = clock()))
    }

    override suspend fun queueSealed(recipient: String, content: Content, type: Int, body: String) {
        val json = storageJson.encodeToString(Content.serializer(), content)
        db.outbox().insert(OutboxEntity(recipient = recipient, content = json, createdAt = clock(), sealedType = type, sealedBody = body))
    }

    /**
     * Queues and sends; never inside a transaction, for the reason [GroupOutbox] gives. Sends in a finally: a caller
     * cancelled after the row is written, as the database hands back the answer, would leave it to whatever asks next.
     */
    suspend fun enqueue(recipient: String, content: Content) {
        try {
            queue(recipient, content)
        } finally {
            flush()
        }
    }

    /**
     * Sends what waits, now: something new was queued or the connection is back. The pass runs in the outbox's own
     * scope, not the caller's: nobody waits for the network to queue the next item, and a caller that goes away, a
     * screen closed mid-send, stops neither the pass nor the one more it may owe.
     *
     * A call while a pass is running is not dropped: it asks for one more pass, run as soon as the running one ends,
     * failed or not. So a message queued meanwhile goes out now rather than at the next send or reconnect, even if
     * it has to retry, ahead of itself, an item the server has just told to wait. The job returned is done when this
     * call's passes are, or at once when it found a pass under way.
     */
    override fun flush(): Job {
        asked.set(true)
        return scope.launch { passWhileAsked() }
    }

    private suspend fun passWhileAsked() {
        // Checked after every pass, failed ones too: a call during it found the lock taken and left only the flag.
        while (asked.get() && lock.tryLock()) passAndUnlock()
    }

    /**
     * The pass of a retry timer. One already under way is left alone: it tries the waiting items itself, and asking
     * it for another would knock again, right after its refusal, on a server that has just named when to come back.
     */
    private suspend fun retry() {
        if (!lock.tryLock()) return
        passAndUnlock()
        passWhileAsked()
    }

    /**
     * One pass over the queue in order, with [lock] held; the first item that has to wait keeps the rest behind it.
     * Its caller then checks for a pass asked for meanwhile. A pass that throws, a write to the database that failed,
     * skips that check, so it makes it itself once the lock is free, and leaves the failure to the scope.
     */
    private suspend fun passAndUnlock() {
        var ended = false
        try {
            asked.set(false)
            for (item in db.outbox().all()) {
                if (!deliver(item)) break
            }
            ended = true
        } finally {
            lock.unlock()
            if (!ended && asked.get()) scope.launch { passWhileAsked() }
        }
    }

    /**
     * Returns false when the rest of the queue should wait, in order, for a later pass: the network is down,
     * the server is busy or this phone is signed out. Only a refusal of this very item fails it.
     */
    private suspend fun deliver(item: OutboxEntity, signedInAgain: Boolean = false): Boolean {
        val content = try {
            storageJson.decodeFromString(Content.serializer(), item.content)
        } catch (_: IllegalArgumentException) {
            // Written in a form this version no longer reads: it can never go out, and kept it would hold up the rest.
            db.outbox().delete(item.id)
            log.record(EventCode.SendFailed)
            return true
        }
        return try {
            transmit(item, content)
            true
        } catch (e: ApiException) {
            when {
                // A token refused as soon as it was issued means the account is refused, not the token: the next
                // send or connection tries again, while a timer would sign in every few seconds for good.
                e.status == UNAUTHORIZED -> if (signedInAgain) false else signInAndRetry(item)
                e.status in CLIENT_ERRORS && e.status != TOO_MANY -> {
                    dropFailed(item, content)
                    true
                }
                else -> {
                    postpone(item, e.retryAfterMillis)
                    false
                }
            }
        } catch (_: IOException) {
            postpone(item, retryAfterMillis = null)
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Broken keys or an untrusted identity: this item can never go out as it is.
            dropFailed(item, content)
            true
        }
    }

    /** Encrypts and sends one item and takes it off the queue; a recipient without keys fails it. */
    private suspend fun transmit(item: OutboxEntity, content: Content) {
        val sealedType = item.sealedType
        val sealedBody = item.sealedBody
        if (sealedType != null && sealedBody != null) {
            api.send(item.recipient, OutgoingMessage("${content.id}:${item.recipient}", sealedType, sealedBody))
            db.outbox().delete(item.id)
            return
        }
        if (!withContext(Dispatchers.IO) { crypto.hasSession(item.recipient) }) {
            val bundle = api.bundle(item.recipient, linkOf(item.recipient))
            if (bundle == null) {
                dropFailed(item, content)
                return
            }
            withContext(Dispatchers.IO) { crypto.startSession(bundle) }
            shareProfile(item.recipient)
        }
        val sealed = withContext(Dispatchers.IO) { crypto.encrypt(item.recipient, content) }
        api.send(item.recipient, OutgoingMessage(content.id, sealed.type, sealed.body, item.ephemeral))
        db.outbox().delete(item.id)
        if (content.body is Body.Text) db.messages().advanceStatus(listOf(content.id), me, SendStatus.Sent)
    }

    /**
     * The token expired or was revoked: a fresh one, then the same item once more. A failed sign-in is not thrown
     * on: this runs inside deliver's own catch, so a throw would end the whole pass with no retry set, and the queue
     * would wait for the next send.
     */
    private suspend fun signInAndRetry(item: OutboxEntity): Boolean {
        try {
            // Signing in reads the identity key and stores the token, and Room refuses the main thread.
            withContext(Dispatchers.IO) { accounts.signIn(api, me) }
        } catch (e: ApiException) {
            // Refused outright, this phone no longer holds the account; a timer would only ask again and again.
            if (e.status == TOO_MANY || e.status !in CLIENT_ERRORS) postpone(item, e.retryAfterMillis)
            return false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The network, or an answer or a write that went wrong this time.
            postpone(item, retryAfterMillis = null)
            return false
        }
        return deliver(item, signedInAgain = true)
    }

    /** Keeps [item] for a later pass, after the pause the server asked for or the usual one. */
    private suspend fun postpone(item: OutboxEntity, retryAfterMillis: Long?) {
        db.outbox().bumpAttempts(item.id)
        scheduleRetry(retryAfterMillis?.coerceIn(MIN_RETRY_MILLIS, MAX_RETRY_MILLIS) ?: retryMillis)
    }

    /**
     * One timer at a time, set by the latest failure: it knows best when to ask again, be it a busy server's
     * longer pause or a short one after the network dropped. A timer that already fired is not cancelled, as the
     * pass it started may be the one asking.
     */
    private fun scheduleRetry(afterMillis: Long) {
        // Started in place, whatever the scope's dispatcher: it is the pending timer and counting before the failed
        // pass goes on, not at some later turn of a thread, by when the time it waits for may have run.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val self = coroutineContext.job
            synchronized(timerLock) {
                timer?.cancel()
                timer = self
            }
            delay(afterMillis)
            // Replaced as it came due, it leaves the asking to the newer timer. Past this check nothing cancels it, so
            // its pass cannot die halfway, after taking over a request it would then never carry out.
            val due = synchronized(timerLock) { (timer === self).also { if (it) timer = null } }
            if (due) retry()
        }
    }

    private suspend fun dropFailed(item: OutboxEntity, content: Content) {
        db.outbox().delete(item.id)
        if (content.body is Body.Text) db.messages().setStatus(listOf(content.id), me, SendStatus.Failed.name)
        log.record(EventCode.SendFailed)
    }

    companion object {
        const val RETRY_MILLIS = 15_000L

        /** Bounds on a server's Retry-After: no busy loop on a zero, no queue parked for hours on a typo. */
        private const val MIN_RETRY_MILLIS = 1_000L
        private const val MAX_RETRY_MILLIS = 10 * 60_000L

        private const val UNAUTHORIZED = 401
        private const val TOO_MANY = 429
        private val CLIENT_ERRORS = 400..499
    }
}
