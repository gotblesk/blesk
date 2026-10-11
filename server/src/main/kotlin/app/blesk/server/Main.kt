package app.blesk.server

import app.blesk.server.attachments.AttachmentStorage
import app.blesk.server.attachments.FileSystemStorage
import app.blesk.server.attachments.MinioStorage
import app.blesk.server.db.Database
import app.blesk.server.http.RateLimiter
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("blesk.server")

fun main() {
    val config = Config.fromEnvironment()
    // Postgres and MinIO may still be starting when the container comes up.
    val dataSource = retrying("database") { Database.pool(config.database) }
    Database.migrate(dataSource)
    val services = Services(
        database = Database(dataSource),
        storage = retrying("storage") { storage(config.storage) },
        authRateLimiter = RateLimiter(config.authRateLimit),
        postRateLimiter = RateLimiter(config.postRateLimit),
        trustProxy = config.trustProxy,
    )
    log.info("Listening on port {}", config.port)
    embeddedServer(Netty, port = config.port) { bleskModule(services, config.maintenanceInterval) }.start(wait = true)
}

private fun storage(config: StorageConfig): AttachmentStorage = when (config) {
    is StorageConfig.FileSystem -> FileSystemStorage(config.directory)
    is StorageConfig.Minio -> MinioStorage(config.endpoint, config.accessKey, config.secretKey, config.bucket).also { it.ensureBucket() }
}

private fun <T> retrying(what: String, attempts: Int = 30, block: () -> T): T {
    repeat(attempts - 1) { attempt ->
        @Suppress("TooGenericExceptionCaught")
        try {
            return block()
        } catch (failure: Exception) {
            log.warn("Waiting for {} ({}), attempt {}", what, failure.javaClass.simpleName, attempt + 1)
            Thread.sleep(RETRY_DELAY_MILLIS)
        }
    }
    return block()
}

private const val RETRY_DELAY_MILLIS = 2_000L
