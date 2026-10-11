package app.blesk.server.bots

import app.blesk.server.BleskJson
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.Update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val log = LoggerFactory.getLogger("blesk.bots")

/**
 * Which webhook addresses the server agrees to call. Normally only https and only public addresses,
 * so a bot owner cannot make the server reach Postgres, MinIO or anything else on its private network.
 *
 * The host is resolved before every attempt and every address it resolves to must be public. The HTTP
 * client resolves it once more when connecting, so a DNS server answering differently within that
 * instant could still slip through; redirects are never followed, which closes the easier route.
 *
 * [allowPrivateAddresses] also allows plain http; it exists for tests and local development only.
 */
class WebhookPolicy(private val allowPrivateAddresses: Boolean) {
    suspend fun allows(url: String): Boolean {
        val uri = parse(url) ?: return false
        val schemeAllowed = uri.scheme == "https" || (allowPrivateAddresses && uri.scheme == "http")
        if (!schemeAllowed || uri.rawUserInfo != null || uri.host.isNullOrEmpty()) return false
        if (allowPrivateAddresses) return true
        val addresses = withContext(Dispatchers.IO) {
            try {
                InetAddress.getAllByName(uri.host).toList()
            } catch (_: UnknownHostException) {
                emptyList()
            }
        }
        return addresses.isNotEmpty() && addresses.all(::isPublic)
    }

    private fun parse(url: String): URI? = if (url.length > MAX_URL_LENGTH) {
        null
    } else {
        try {
            URI(url).takeIf { it.isAbsolute }
        } catch (_: URISyntaxException) {
            null
        }
    }

    companion object {
        const val MAX_URL_LENGTH = 512

        fun isPublic(address: InetAddress): Boolean {
            val local = address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
            if (local) return false
            val bytes = address.address
            val first = bytes[0].toInt() and BYTE
            val second = bytes[1].toInt() and BYTE
            return when (address) {
                // 0.0.0.0/8, carrier-grade NAT 100.64.0.0/10, and the reserved 240.0.0.0/4.
                is Inet4Address -> first != 0 && !(first == 100 && second in 64..127) && first < 240
                // Unique local fc00::/7.
                is Inet6Address -> first and 0xFE != 0xFC
                else -> false
            }
        }

        private const val BYTE = 0xFF
    }
}

/** Posts updates to bots that set a webhook. A failed delivery leaves the update for getUpdates. */
class BotWebhooks(private val policy: WebhookPolicy, private val retryDelays: List<Duration> = DefaultRetryDelays) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(ConnectTimeout.toJavaDuration())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    suspend fun allows(url: String) = policy.allows(url)

    /** True once the bot answered 2xx; tries a few times with growing pauses. */
    suspend fun deliver(webhook: Webhook, update: Update): Boolean {
        val body = BleskJson.encodeToString(Update.serializer(), update)
        retryDelays.forEachIndexed { attempt, pause ->
            delay(pause)
            if (!policy.allows(webhook.url)) return false
            if (post(webhook, body)) return true
            log.info("Webhook attempt {} failed", attempt + 1)
        }
        return false
    }

    private suspend fun post(webhook: Webhook, body: String): Boolean {
        val request = HttpRequest.newBuilder(URI(webhook.url))
            .timeout(RequestTimeout.toJavaDuration())
            .header("Content-Type", "application/json")
            .apply { webhook.secret?.let { header(Bots.WEBHOOK_SECRET_HEADER, it) } }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return try {
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).await().statusCode() in SUCCESS
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    companion object {
        /** The pause before each attempt. */
        val DefaultRetryDelays = listOf(0.seconds, 1.seconds, 5.seconds)

        private val ConnectTimeout = 5.seconds
        private val RequestTimeout = 10.seconds
        private val SUCCESS = 200..299
    }
}
