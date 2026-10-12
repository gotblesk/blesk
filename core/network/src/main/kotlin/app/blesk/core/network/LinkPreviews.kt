package app.blesk.core.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.blesk.shared.protocol.LinkPreview
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Base64

/**
 * Builds the preview card of a link on the sender's phone, so the server never learns which
 * pages people share. Only https pages are fetched, and only the first few hundred kilobytes.
 */
class LinkPreviews(
    private val client: HttpClient = HttpClient(OkHttp) {
        followRedirects = true
        engine { config { followSslRedirects(false).proxySelector(NetworkProxy.selector) } }
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MILLIS
            connectTimeoutMillis = TIMEOUT_MILLIS
        }
    },
) {
    /** The card for the first https link in [text], or null when there is none or the page cannot be read. */
    suspend fun forText(text: String): LinkPreview? = firstLink(text)?.let { fetch(it) }

    /** The card for [url], or null when the page has no title or cannot be reached. */
    suspend fun fetch(url: String): LinkPreview? = try {
        val page = read(url, HTML_LIMIT) { it.match(ContentType.Text.Html) }
        page?.let {
            val charset = runCatching { page.type.charset() }.getOrNull() ?: Charsets.UTF_8
            val meta = PageMeta.parse(String(page.bytes, charset), page.url) ?: return@let null
            LinkPreview(url, meta.title, meta.description, meta.image?.let { image(it) })
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun image(url: String): String? {
        val bytes = read(url, IMAGE_LIMIT) { it.contentType == ContentType.Image.Any.contentType }?.bytes ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth < MIN_IMAGE || bounds.outHeight < MIN_IMAGE) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= IMAGE_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val width = minOf(IMAGE_SIDE, decoded.width)
        val scaled = Bitmap.createScaledBitmap(decoded, width, decoded.height * width / decoded.width, true)
        var quality = JPEG_QUALITY
        var jpeg: ByteArray
        do {
            jpeg = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            quality -= QUALITY_STEP
        } while (jpeg.size > LinkPreview.MAX_IMAGE_BYTES && quality > MIN_QUALITY)
        return if (jpeg.size > LinkPreview.MAX_IMAGE_BYTES) null else Base64.getEncoder().encodeToString(jpeg)
    }

    /** The body of an https [url] up to [limit] bytes, with the address it ended at; null if it is not wanted. */
    private suspend fun read(url: String, limit: Int, accept: (ContentType) -> Boolean): Page? {
        if (!url.startsWith("https://", ignoreCase = true)) return null
        return client.prepareGet(url) { header("Accept-Language", "ru, en;q=0.8") }.execute { response ->
            val type = response.contentType() ?: return@execute null
            val finalUrl = response.call.request.url.toString()
            if (!response.status.isSuccess() || !accept(type) || !finalUrl.startsWith("https://")) return@execute null
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(limit)
            var size = 0
            while (size < limit) {
                val read = channel.readAvailable(buffer, size, limit - size)
                if (read < 0) break
                size += read
            }
            Page(finalUrl, type, buffer.copyOf(size))
        }
    }

    private class Page(val url: String, val type: ContentType, val bytes: ByteArray)

    companion object {
        private val link = Regex("""https://[^\s<>«»"]+""", RegexOption.IGNORE_CASE)
        private const val TRAILING = ".,;:!?)»"

        /** The first https address in [text], without the punctuation a sentence puts after it. */
        fun firstLink(text: String): String? = link.find(text)?.value?.trimEnd { it in TRAILING }

        private const val TIMEOUT_MILLIS = 5_000L
        const val HTML_LIMIT = 256 * 1024
        const val IMAGE_LIMIT = 2 * 1024 * 1024
        const val IMAGE_SIDE = 480
        const val MIN_IMAGE = 64
        const val JPEG_QUALITY = 75
        const val QUALITY_STEP = 15
        const val MIN_QUALITY = 30
    }
}

/** Title, description and picture of a page, from its Open Graph tags or, failing them, its `<title>`. */
internal data class PageMeta(val title: String, val description: String, val image: String?) {
    companion object {
        private val metaTag = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
        private val attribute = Regex("""([a-zA-Z:-]+)\s*=\s*("([^"]*)"|'([^']*)')""")
        private val titleTag = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)
        private val entity = Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);""")

        fun parse(html: String, pageUrl: String): PageMeta? {
            val tags = metaTag.findAll(html).mapNotNull { tag ->
                val attrs = attribute.findAll(tag.value).associate { match ->
                    match.groupValues[1].lowercase() to match.groupValues[3].ifEmpty { match.groupValues[4] }
                }
                val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: return@mapNotNull null
                attrs["content"]?.let { key to unescape(it).trim() }
            }.filter { it.second.isNotEmpty() }.toMap()
            val title = tags["og:title"] ?: tags["twitter:title"] ?: titleTag.find(html)?.groupValues?.get(1)?.let(::unescape)?.trim()
            if (title.isNullOrEmpty()) return null
            val description = tags["og:description"] ?: tags["twitter:description"] ?: tags["description"] ?: ""
            val image = (tags["og:image"] ?: tags["twitter:image"])?.let { resolve(pageUrl, it) }
            return PageMeta(
                title.take(LinkPreview.MAX_TITLE),
                description.take(LinkPreview.MAX_DESCRIPTION),
                image?.takeIf { it.startsWith("https://") },
            )
        }

        private fun resolve(base: String, path: String): String? = runCatching { URI(base).resolve(path.trim()).toString() }.getOrNull()

        private fun unescape(text: String): String = entity.replace(text) { match ->
            when (val name = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else -> {
                    val code = if (name.startsWith("#x")) name.drop(2).toIntOrNull(HEX) else name.drop(1).toIntOrNull()
                    code?.takeIf(Character::isValidCodePoint)?.let { String(Character.toChars(it)) } ?: match.value
                }
            }
        }

        private const val HEX = 16
    }
}
