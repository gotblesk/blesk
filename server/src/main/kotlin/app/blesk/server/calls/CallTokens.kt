package app.blesk.server.calls

import app.blesk.server.LiveKitConfig
import app.blesk.shared.protocol.CallToken
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Duration
import java.time.Instant

/**
 * LiveKit access tokens: HS256 JWTs signed with the API secret shared with the LiveKit server.
 * The grant lets one account into one room; what is said there stays end-to-end encrypted.
 */
class CallTokens(private val config: LiveKitConfig) {
    private val algorithm = Algorithm.HMAC256(config.apiSecret)

    fun issue(identity: String, room: String, now: Instant): CallToken {
        val token = JWT.create()
            .withIssuer(config.apiKey)
            .withSubject(identity)
            .withNotBefore(now)
            .withExpiresAt(now + LIFETIME)
            .withClaim(
                "video",
                mapOf(
                    "room" to room,
                    "roomJoin" to true,
                    "canPublish" to true,
                    "canSubscribe" to true,
                    "canPublishData" to true,
                ),
            )
            .sign(algorithm)
        return CallToken(url = config.publicUrl, token = token)
    }

    companion object {
        /** Only joining needs a valid token; a call that is already running outlives it. */
        val LIFETIME: Duration = Duration.ofHours(6)
    }
}
