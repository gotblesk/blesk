package app.blesk.server

import app.blesk.server.db.Database
import app.blesk.server.db.DatabaseHealth
import app.blesk.shared.protocol.Api
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class HealthTest {
    @Test
    fun `health answers ok while the database works`() = serverTest { harness ->
        val response = harness.client.get(Api.HEALTH)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
    }

    @Test
    fun `health answers unavailable when the database refuses`() = serverTest(dataSource = dataSource { throw SQLException("down") }) {
        val response = client.get(Api.HEALTH)
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("""{"status":"unavailable"}""", response.bodyAsText())
    }

    @Test
    fun `a hanging database is answered within the timeout by one check however often it is asked`(): Unit = runBlocking {
        val release = CountDownLatch(1)
        val connects = AtomicInteger()
        val hanging = dataSource {
            connects.incrementAndGet()
            release.await(10, TimeUnit.SECONDS)
            throw SQLException("timed out")
        }
        val health = DatabaseHealth(Database(hanging), timeout = 200.milliseconds)
        try {
            val elapsed = measureTime {
                val answers = List(5) { async { health.isUp() } }.awaitAll()
                assertEquals(List(5) { false }, answers)
                assertFalse(health.isUp())
            }
            assertTrue("answered in $elapsed", elapsed < 2.seconds)
            assertEquals(1, connects.get())
        } finally {
            release.countDown()
        }
    }

    private fun dataSource(connect: () -> Connection): DataSource =
        Proxy.newProxyInstance(DataSource::class.java.classLoader, arrayOf(DataSource::class.java)) { _, method, _ ->
            if (method.name == "getConnection") connect() else throw UnsupportedOperationException(method.name)
        } as DataSource
}
