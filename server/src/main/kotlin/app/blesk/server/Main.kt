package app.blesk.server

import app.blesk.server.attachments.AttachmentStorage
import app.blesk.server.attachments.FileSystemStorage
import app.blesk.server.attachments.MinioStorage
import app.blesk.server.bots.WebhookPolicy
import app.blesk.server.calls.CallTokens
import app.blesk.server.db.Database
import app.blesk.server.http.RateLimiter
import app.blesk.server.invites.InviteCommand
import app.blesk.server.invites.InviteRepository
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("blesk.server")

fun main(args: Array<String>) {
    val command = if (args.isEmpty()) null else InviteCommand.parse(args.asList()) ?: usageError()
    val config = Config.fromEnvironment()
    // Postgres and MinIO may still be starting when the container comes up.
    val dataSource = retrying("database") { Database.pool(config.database) }
    Database.migrate(dataSource)
    if (command != null) {
        val links = runBlocking { command.run(InviteRepository(Database(dataSource)), Instant.now()) }
        links.forEach(::println)
        dataSource.close()
        return
    }
    val services = Services(
        database = Database(dataSource),
        storage = retrying("storage") { storage(config.storage) },
        requestRateLimiter = RateLimiter(config.requestRateLimit),
        authRateLimiter = RateLimiter(config.authRateLimit),
        usernameCheckLimiter = RateLimiter(config.usernameCheckRateLimit),
        postRateLimiter = RateLimiter(config.postRateLimit),
        trustProxy = config.trustProxy,
        callRateLimiter = RateLimiter(config.callRateLimit),
        callTokens = config.liveKit?.let(::CallTokens),
        botRateLimiter = RateLimiter(config.botRateLimit),
        botApiRateLimiter = RateLimiter(config.botApiRateLimit),
        webhookPolicy = WebhookPolicy(allowPrivateAddresses = false),
        registration = config.registration,
    )
    log.info("Calls {}", if (config.liveKit != null) "enabled" else "disabled")
    log.info("Registration {}", config.registration.name.lowercase())
    log.info("Listening on port {}", config.port)
    embeddedServer(Netty, port = config.port) { bleskModule(services, config.maintenanceInterval) }.start(wait = true)
}

/** Exit status 2, as command-line tools answer a usage mistake. */
private fun usageError(): Nothing {
    System.err.println(InviteCommand.USAGE)
    exitProcess(USAGE_ERROR)
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
private const val USAGE_ERROR = 2
