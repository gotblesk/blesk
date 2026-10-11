package app.blesk.server

import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class Config(
    val port: Int,
    val database: DatabaseConfig,
    val storage: StorageConfig,
    /** Behind Caddy the peer address is the proxy, so the client address comes from X-Forwarded-For. */
    val trustProxy: Boolean,
    val authRateLimit: RateLimitConfig,
    /** Per account, for posts in public spaces. */
    val postRateLimit: RateLimitConfig,
    val maintenanceInterval: Duration,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): Config {
            fun value(name: String) = env[name]?.takeIf { it.isNotBlank() }
            fun required(name: String) = requireNotNull(value(name)) { "$name is not set" }

            val storage = when (val kind = value("BLESK_STORAGE") ?: "minio") {
                "minio" -> StorageConfig.Minio(
                    endpoint = required("BLESK_MINIO_ENDPOINT"),
                    accessKey = required("BLESK_MINIO_ACCESS_KEY"),
                    secretKey = required("BLESK_MINIO_SECRET_KEY"),
                    bucket = value("BLESK_MINIO_BUCKET") ?: "blesk-attachments",
                )
                "fs" -> StorageConfig.FileSystem(Path.of(value("BLESK_FS_DIR") ?: "data/attachments"))
                else -> error("BLESK_STORAGE must be minio or fs, got $kind")
            }
            return Config(
                port = value("BLESK_PORT")?.toInt() ?: DEFAULT_PORT,
                database = DatabaseConfig(
                    url = value("BLESK_DB_URL") ?: "jdbc:postgresql://localhost:5432/blesk",
                    user = value("BLESK_DB_USER") ?: "blesk",
                    password = value("BLESK_DB_PASSWORD") ?: "",
                ),
                storage = storage,
                trustProxy = value("BLESK_TRUST_PROXY")?.toBooleanStrict() ?: false,
                authRateLimit = RateLimitConfig(
                    capacity = value("BLESK_AUTH_RATE_BURST")?.toInt() ?: DEFAULT_BURST,
                    refillPerMinute = value("BLESK_AUTH_RATE_PER_MINUTE")?.toInt() ?: DEFAULT_PER_MINUTE,
                ),
                postRateLimit = (value("BLESK_POST_RATE_PER_MINUTE")?.toInt() ?: DEFAULT_POST_PER_MINUTE).let { RateLimitConfig(it, it) },
                maintenanceInterval = DEFAULT_MAINTENANCE_INTERVAL,
            )
        }

        private const val DEFAULT_PORT = 8080
        private const val DEFAULT_BURST = 20
        private const val DEFAULT_PER_MINUTE = 30
        private const val DEFAULT_POST_PER_MINUTE = 30
        private val DEFAULT_MAINTENANCE_INTERVAL = 15.minutes
    }
}

data class DatabaseConfig(val url: String, val user: String, val password: String) {
    override fun toString() = "DatabaseConfig(url=$url, user=$user)"
}

sealed interface StorageConfig {
    data class Minio(val endpoint: String, val accessKey: String, val secretKey: String, val bucket: String) : StorageConfig {
        override fun toString() = "Minio(endpoint=$endpoint, bucket=$bucket)"
    }

    data class FileSystem(val directory: Path) : StorageConfig
}

data class RateLimitConfig(val capacity: Int, val refillPerMinute: Int)
