package app.blesk.calls

import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallsManager
import app.blesk.core.calls.CallManager
import app.blesk.core.data.calls.CallOutcome
import app.blesk.core.data.calls.CallPhase
import app.blesk.core.data.calls.CallSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Tells Android about each call through core-telecom, so the system treats it as a call:
 * Bluetooth and car buttons work, other apps pause their sound, and the status bar shows it.
 * Telecom misbehaves on some phones; then the call simply goes on without it.
 */
internal class TelecomBridge(context: Context, private val scope: CoroutineScope) {
    private val calls = CallsManager(context)
    private val registered = runCatching {
        calls.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING)
    }.isSuccess
    private var tracked: String? = null

    fun track(manager: CallManager, session: CallSession) {
        val relevant = !session.voiceChannel && session.phase != CallPhase.Ended
        if (!registered || !relevant || tracked == session.id) return
        tracked = session.id
        val attributes = CallAttributesCompat(
            displayName = session.title,
            address = Uri.parse("blesk:${session.conversationId}"),
            direction = if (session.incoming) CallAttributesCompat.DIRECTION_INCOMING else CallAttributesCompat.DIRECTION_OUTGOING,
            callType = if (session.video) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
        )
        scope.launch {
            runCatching {
                calls.addCall(
                    attributes,
                    onAnswer = { manager.accept() },
                    onDisconnect = { manager.hangUp() },
                    onSetActive = {},
                    onSetInactive = {},
                ) { follow(manager, session) }
            }
            if (tracked == session.id) tracked = null
        }
    }

    private fun CallControlScope.follow(manager: CallManager, first: CallSession) {
        launch {
            var answered = false
            manager.session.collect { current ->
                if (current?.id != first.id || current.phase == CallPhase.Ended) {
                    disconnect(DisconnectCause(causeOf(current?.outcome)))
                    return@collect
                }
                if (current.phase == CallPhase.Active && !answered) {
                    answered = true
                    if (current.incoming) {
                        answer(if (current.video) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL)
                    } else {
                        setActive()
                    }
                }
            }
        }
    }

    private fun causeOf(outcome: CallOutcome?) = when (outcome) {
        CallOutcome.Declined -> DisconnectCause.REJECTED
        CallOutcome.Missed, CallOutcome.NoAnswer -> DisconnectCause.MISSED
        CallOutcome.Busy -> DisconnectCause.BUSY
        CallOutcome.Failed, CallOutcome.Unavailable -> DisconnectCause.ERROR
        CallOutcome.HangUp, null -> DisconnectCause.LOCAL
    }
}
