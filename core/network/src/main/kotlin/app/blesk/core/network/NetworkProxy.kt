package app.blesk.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

enum class ProxyKind { Socks5, Http }

data class ProxyConfig(val kind: ProxyKind, val host: String, val port: Int) {
    /** Unresolved, so with SOCKS5 even the server's name is looked up by the proxy, not by the phone. */
    fun toProxy(): Proxy = Proxy(
        if (kind == ProxyKind.Socks5) Proxy.Type.SOCKS else Proxy.Type.HTTP,
        InetSocketAddress.createUnresolved(host, port),
    )
}

/**
 * The proxy every connection of the app goes through, or none. Clients read it on each new
 * connection, and when it changes the socket reconnects, so nothing keeps the old route. With a
 * proxy set there is no fallback to a direct connection.
 */
object NetworkProxy {
    private val state = MutableStateFlow<ProxyConfig?>(null)

    val current: StateFlow<ProxyConfig?> = state.asStateFlow()

    fun set(config: ProxyConfig?) {
        state.value = config
    }

    val selector: ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> = listOf(state.value?.toProxy() ?: Proxy.NO_PROXY)

        override fun connectFailed(uri: URI?, address: SocketAddress?, failure: IOException?) = Unit
    }

    /** Whether something answers at the proxy's address; for checking it without a server. */
    suspend fun reachable(config: ProxyConfig): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            java.net.Socket().use { it.connect(InetSocketAddress(config.host, config.port), CHECK_TIMEOUT_MILLIS) }
        }.isSuccess
    }

    const val CHECK_TIMEOUT_MILLIS = 5_000
}
