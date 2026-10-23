package app.blesk.core.network

import app.blesk.shared.protocol.ServerInfo
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BleskApiInfoTest {
    private fun api(body: String, status: HttpStatusCode = HttpStatusCode.OK, type: String = "application/json") =
        BleskApi(ServerAddress.DEFAULT, MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, type)) })

    private fun notBlesk(api: BleskApi) = assertThrows(NotBleskException::class.java) { runBlocking { api.info() } }

    @Test
    fun `a page instead of the server is not blesk`() {
        notBlesk(api("<html><body>Войдите в сеть Wi-Fi</body></html>", type = "text/html"))
        notBlesk(api("<html>Not found</html>", HttpStatusCode.NotFound, "text/html"))
    }

    @Test
    fun `another service's json is not blesk`() {
        notBlesk(api("""{"status":"ok"}"""))
        notBlesk(api("""["blesk"]"""))
        notBlesk(api("""{"version":1,"invites":"yes"}"""))
    }

    @Test
    fun `an older server's info reads with the defaults`() = runBlocking {
        assertEquals(ServerInfo(name = "blesk", version = 1), api("""{"name":"blesk","version":1}""").info())
    }

    @Test
    fun `a server that is down is not mistaken for another one`() {
        val down = api("", HttpStatusCode.ServiceUnavailable, "text/plain")
        val error = assertThrows(ApiException::class.java) { runBlocking { down.info() } }
        assertEquals(503, error.status)
    }
}
