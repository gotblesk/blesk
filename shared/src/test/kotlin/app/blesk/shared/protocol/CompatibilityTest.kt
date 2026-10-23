package app.blesk.shared.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What this app reads from servers older than its protocol, and the names every server keeps back. */
class CompatibilityTest {
    @Test
    fun `an older server's info reads with safe defaults`() {
        val info = Json.decodeFromString<ServerInfo>("""{"name":"blesk","version":1,"calls":true}""")
        assertNull(info.invites)
        assertEquals(1, info.minClientVersion)
        assertNull(info.officialChannel)
        assertTrue(info.calls)
    }

    @Test
    fun `a space from an older server is not official`() {
        val space = Json.decodeFromString<SpaceInfo>(
            """{"handle":"hike","kind":"channel","title":"Походы","about":"","members":3,"channels":[]}""",
        )
        assertFalse(space.official)
    }

    @Test
    fun `service handles and bot names are reserved like usernames`() {
        listOf("mine", "blesk", "b1esk_news", "admin", "6lesk", "4dmin").forEach { assertTrue(it, Spaces.isReservedHandle(it)) }
        listOf("hike", "egor_club", "2gis", "gleb_leskov").forEach { assertFalse(it, Spaces.isReservedHandle(it)) }
        listOf("blesk_bot", "support_bot", "news_bot", "help_bot").forEach { assertTrue(it, Bots.isReservedName(it)) }
        assertFalse(Bots.isReservedName("weather_bot"))
    }
}
