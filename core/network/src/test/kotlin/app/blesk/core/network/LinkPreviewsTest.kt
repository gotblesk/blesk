package app.blesk.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPreviewsTest {

    @Test
    fun `open graph tags win over the title`() {
        val html = """
            <html><head><title>Запасной</title>
            <meta property="og:title" content="Блеск &amp; свет">
            <meta content='Про &#171;стекло&#187;' name='description'>
            <meta property="og:image" content="/cover.jpg">
            </head></html>
        """.trimIndent()
        val meta = PageMeta.parse(html, "https://example.org/post/1")
        assertEquals(PageMeta("Блеск & свет", "Про «стекло»", "https://example.org/cover.jpg"), meta)
    }

    @Test
    fun `a page without a title gives no card and plain http images are dropped`() {
        assertNull(PageMeta.parse("<html><body>пусто</body></html>", "https://example.org"))
        val meta = PageMeta.parse("<title>Т</title><meta property=\"og:image\" content=\"http://x.org/a.png\">", "https://example.org")
        assertNull(meta?.image)
    }

    @Test
    fun `first link skips plain http and trailing punctuation`() {
        assertEquals("https://blesk.app/a?b=1", LinkPreviews.firstLink("см. http://old.org и https://blesk.app/a?b=1."))
        assertNull(LinkPreviews.firstLink("www.blesk.app"))
    }

    @Test
    fun `pages in another charset and non pages`() = runBlocking {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/page" -> respond(
                    "<title>Новости</title>".toByteArray(charset("windows-1251")),
                    headers = headersOf(HttpHeaders.ContentType, "text/html; charset=windows-1251"),
                )
                else -> respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val previews = LinkPreviews(HttpClient(engine))
        assertEquals("Новости", previews.fetch("https://news.example/page")?.title)
        assertNull(previews.fetch("https://news.example/api"))
        assertNull(previews.fetch("http://news.example/page"))
    }
}
