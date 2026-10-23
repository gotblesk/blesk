package app.blesk.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    private val pin = "sha256/" + "A".repeat(43) + "="

    @Test
    fun `qr with pin`() {
        assertEquals(ServerAddress("https://1.2.3.4", pin), ServerAddress.parse("blesk://server?url=https://1.2.3.4&pin=$pin"))
    }

    @Test
    fun `plus signs in the pin survive`() {
        val plusPin = "sha256/" + "a+b/".repeat(10) + "abc="
        assertEquals(plusPin, ServerAddress.parse("blesk://server?url=https://1.2.3.4&pin=$plusPin")?.pin)
    }

    @Test
    fun `plain host becomes https`() {
        assertEquals(ServerAddress("https://chat.example.org"), ServerAddress.parse("chat.example.org"))
        assertEquals(ServerAddress("https://chat.example.org:8443"), ServerAddress.parse("https://chat.example.org:8443/path"))
    }

    @Test
    fun `the default is blesk's own server without a pin`() {
        assertEquals("https://api.blesk.su", ServerAddress.DEFAULT.url)
        assertEquals("api.blesk.su", ServerAddress.DEFAULT.host)
        assertNull(ServerAddress.DEFAULT.pin)
        assertEquals(ServerAddress.DEFAULT, ServerAddress.parse("api.blesk.su"))
    }

    @Test
    fun `a server link reads back as the same address`() {
        val address = ServerAddress("https://1.2.3.4:8443", "sha256/" + "a+b/".repeat(10) + "abc=")
        assertEquals(address, ServerAddress.parse(address.link()))
    }

    @Test
    fun `blesk's own server is just its url`() {
        assertEquals("https://api.blesk.su", ServerAddress.DEFAULT.link())
        assertEquals(ServerAddress.DEFAULT, ServerAddress.parse(ServerAddress.DEFAULT.link()))
    }

    @Test
    fun `plain http and malformed pins are refused`() {
        assertNull(ServerAddress.parse("http://chat.example.org"))
        assertNull(ServerAddress.parse("blesk://server?url=https://1.2.3.4&pin=sha256/short"))
    }
}
