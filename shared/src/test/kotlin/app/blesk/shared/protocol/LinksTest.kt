package app.blesk.shared.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    private val key = "Qx7_kP2-mN4wZ9aB"
    private val group = "6f1c2a9e-7b40-4d1e"
    private val code = "Hk3_9xQe-Lm2"

    @Test
    fun `a profile link goes to the site and back`() {
        assertEquals("https://blesk.su/@vera.s?k=$key", ProfileLink.format("vera.s", key))
        assertEquals("https://blesk.su/@vera.s", ProfileLink.format("vera.s", null))
        assertEquals(ProfileLink.Parsed("vera.s", key), ProfileLink.parse(ProfileLink.format("vera.s", key)))
        assertEquals(ProfileLink.Parsed("vera.s", null), ProfileLink.parse(ProfileLink.format("vera.s", null)))
    }

    @Test
    fun `a profile link is read in every form it travels in`() {
        val expected = ProfileLink.Parsed("vera.s", key)
        listOf(
            "blesk.su/@vera.s?k=$key",
            "www.blesk.su/@vera.s?k=$key",
            "https://www.blesk.su/@vera.s/?k=$key",
            "  HTTPS://Blesk.su/@Vera.S?k=$key#top ",
            "https://blesk.su/%40vera.s?k=$key",
            "https://blesk.su/@vera.s?utm=qr&k=$key",
            "blesk://u/vera.s?k=$key",
        ).forEach { assertEquals(it, expected, ProfileLink.parse(it)) }
        assertEquals(ProfileLink.Parsed("vera.s", null), ProfileLink.parse("blesk://u/vera.s"))
        assertEquals(ProfileLink.Parsed("vera.s", null), ProfileLink.parse("https://blesk.su/@vera.s?k=short"))
    }

    @Test
    fun `a profile link on another host or with a bad name is not one`() {
        listOf(
            "https://blesk.su.evil.com/@vera.s",
            "evil.com/@vera.s",
            "https://evil.com/?next=https://blesk.su/@vera.s",
            "https://user@blesk.su/@vera.s",
            "https://blesk.su:8443/@vera.s",
            "https://blesk.su/vera.s",
            "https://blesk.su/@vera.s/chat",
            "https://blesk.su/@",
            "https://blesk.su/c/vera_s",
            "blesk://x/vera.s",
            "@vera.s",
        ).forEach { assertNull(it, ProfileLink.parse(it)) }
    }

    @Test
    fun `links to names kept back since keep working`() {
        assertEquals(ProfileLink.Parsed("support1", null), ProfileLink.parse("https://blesk.su/@support1"))
        assertEquals("support1", InviteLink.parse(InviteLink.format(group, "support1", code))?.admin)
    }

    @Test
    fun `a group invite goes to the site and back`() {
        val link = InviteLink.format(group, "vera.s", code)
        assertEquals("https://blesk.su/r/$code?g=$group&a=vera.s", link)
        assertEquals(InviteLink.Parsed(group, "vera.s", code), InviteLink.parse(link))
    }

    @Test
    fun `a group invite is read in every form it travels in`() {
        val expected = InviteLink.Parsed(group, "vera.s", code)
        listOf(
            "blesk.su/r/$code?g=$group&a=vera.s",
            "https://www.blesk.su/r/$code/?a=vera.s&g=$group",
            " http://BLESK.SU/r/$code?g=$group&a=vera.s#join ",
            "blesk://join?g=$group&a=vera.s&c=$code",
        ).forEach { assertEquals(it, expected, InviteLink.parse(it)) }
    }

    @Test
    fun `a group invite on another host or with missing parts is not one`() {
        listOf(
            "https://blesk.su.evil.com/r/$code?g=$group&a=vera.s",
            "evil.com/r/$code?g=$group&a=vera.s",
            "https://blesk.su/r/$code?g=$group",
            "https://blesk.su/r/$code?a=vera.s",
            "https://blesk.su/r/?g=$group&a=vera.s",
            "https://blesk.su/r/short?g=$group&a=vera.s",
            "https://blesk.su/r/$code?g=bad!group&a=vera.s",
            "https://blesk.su/r/$code?g=$group&a=Not Valid",
            "https://blesk.su/i/$code?g=$group&a=vera.s",
            "blesk://join?g=$group&a=vera.s",
            "blesk://joinx?g=$group&a=vera.s&c=$code",
        ).forEach { assertNull(it, InviteLink.parse(it)) }
    }

    @Test
    fun `a channel link goes to the site and back`() {
        assertEquals("https://blesk.su/c/blesk_news", ChannelLink.format("blesk_news"))
        assertEquals("blesk_news", ChannelLink.parse(ChannelLink.format("blesk_news")))
        listOf("blesk.su/c/blesk_news", "https://www.blesk.su/c/Blesk_News/", "https://blesk.su/c/blesk_news?from=site")
            .forEach { assertEquals(it, "blesk_news", ChannelLink.parse(it)) }
    }

    @Test
    fun `a channel link on another host or with a bad handle is not one`() {
        listOf(
            "https://blesk.su.evil.com/c/blesk_news",
            "evil.com/c/blesk_news",
            "https://blesk.su/c/ab",
            "https://blesk.su/c/blesk-news",
            "https://blesk.su/c/blesk_news/posts",
            "https://blesk.su/@blesk_news",
            "blesk_news",
        ).forEach { assertNull(it, ChannelLink.parse(it)) }
    }
}
