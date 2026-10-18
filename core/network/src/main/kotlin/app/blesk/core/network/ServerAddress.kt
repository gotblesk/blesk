package app.blesk.core.network

import java.net.URI
import java.net.URLDecoder

/**
 * Where the server is and, for a server without a domain, the SHA-256 of its public key that
 * the app pins instead of trusting certificate authorities. blesk's own server is [DEFAULT];
 * another one comes from the QR code that `deploy.sh` prints, or is typed in.
 */
data class ServerAddress(val url: String, val pin: String? = null) {
    val host: String get() = URI(url).host

    companion object {
        /** blesk's own server: it has a certificate from a public authority, so nothing is pinned. */
        val DEFAULT = ServerAddress("https://api.blesk.su")

        private val pinPattern = Regex("^sha256/[A-Za-z0-9+/]{43}=$")

        /** Accepts `blesk://server?url=…&pin=sha256/…`, a bare https URL, or a host name. */
        fun parse(raw: String): ServerAddress? {
            val text = raw.trim()
            if (text.startsWith("blesk://server")) {
                val query = URI(text).rawQuery ?: return null
                val params = query.split('&').mapNotNull { part ->
                    val key = part.substringBefore('=', "")
                    val value = part.substringAfter('=', "")
                    // Only %-escapes are decoded: a literal '+' in a Base64 pin is not a space.
                    if (key.isEmpty()) null else key to URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
                }.toMap()
                val url = params["url"]?.let(::normalizeUrl) ?: return null
                val pin = params["pin"]?.takeIf { pinPattern.matches(it) }
                if (params["pin"] != null && pin == null) return null
                return ServerAddress(url, pin)
            }
            return normalizeUrl(text)?.let { ServerAddress(it) }
        }

        private fun normalizeUrl(raw: String): String? {
            val withScheme = if (raw.contains("://")) raw else "https://$raw"
            val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
            if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
            val port = if (uri.port > 0) ":${uri.port}" else ""
            return "https://${uri.host}$port"
        }
    }
}
