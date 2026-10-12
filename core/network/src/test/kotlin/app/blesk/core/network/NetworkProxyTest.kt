package app.blesk.core.network

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

class NetworkProxyTest {

    @After
    fun reset() = NetworkProxy.set(null)

    @Test
    fun `every connection follows the current setting without a direct fallback`() {
        val uri = URI("https://blesk.example")
        assertEquals(listOf(Proxy.NO_PROXY), NetworkProxy.selector.select(uri))
        NetworkProxy.set(ProxyConfig(ProxyKind.Socks5, "10.0.0.2", 1080))
        val chosen = NetworkProxy.selector.select(uri).single()
        assertEquals(Proxy.Type.SOCKS, chosen.type())
        // Unresolved: the proxy, not the phone, looks up the name.
        assertEquals(true, (chosen.address() as InetSocketAddress).isUnresolved)
    }
}
