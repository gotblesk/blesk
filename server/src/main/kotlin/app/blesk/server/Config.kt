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
    /** Per client address, for every request but /health: a floor under the narrower limits below. */
    val requestRateLimit: RateLimitConfig,
    val authRateLimit: RateLimitConfig,
    /** Per account, for posts in public spaces. */
    val postRateLimit: RateLimitConfig,
    val maintenanceInterval: Duration,
    /** Null when no LiveKit server is set up; calls are then switched off. */
    val liveKit: LiveKitConfig?,
    /** Per account, for call tokens. */
    val callRateLimit: RateLimitConfig,
    /** Per account, for creating bots and writing to them. */
    val botRateLimit: RateLimitConfig,
    /** Per bot, for the Bot API. */
    val botApiRateLimit: RateLimitConfig,
    val registration: RegistrationMode,
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
                requestRateLimit = RateLimitConfig(
                    capacity = value("BLESK_REQUEST_RATE_BURST")?.toInt() ?: DEFAULT_REQUEST_BURST,
                    refillPerMinute = value("BLESK_REQUEST_RATE_PER_MINUTE")?.toInt() ?: DEFAULT_REQUESTS_PER_MINUTE,
                ),
                authRateLimit = RateLimitConfig(
                    capacity = value("BLESK_AUTH_RATE_BURST")?.toInt() ?: DEFAULT_BURST,
                    refillPerMinute = value("BLESK_AUTH_RATE_PER_MINUTE")?.toInt() ?: DEFAULT_PER_MINUTE,
                ),
                postRateLimit = perMinute(value("BLESK_POST_RATE_PER_MINUTE"), DEFAULT_POST_PER_MINUTE),
                maintenanceInterval = DEFAULT_MAINTENANCE_INTERVAL,
                liveKit = liveKit(::value),
                callRateLimit = perMinute(value("BLESK_CALL_RATE_PER_MINUTE"), DEFAULT_CALL_PER_MINUTE),
                botRateLimit = perMinute(value("BLESK_BOT_RATE_PER_MINUTE"), DEFAULT_BOT_PER_MINUTE),
                botApiRateLimit = perMinute(value("BLESK_BOT_API_RATE_PER_MINUTE"), DEFAULT_BOT_API_PER_MINUTE),
                registration = registration(value("BLESK_REGISTRATION")),
            )
        }

        private fun perMinute(setting: String?, default: Int): RateLimitConfig =
            (setting?.toInt() ?: default).let { RateLimitConfig(capacity = it, refillPerMinute = it) }

        private fun registration(setting: String?): RegistrationMode = when (setting ?: "invite") {
            "invite" -> RegistrationMode.Invite
            "open" -> RegistrationMode.Open
            else -> error("BLESK_REGISTRATION must be invite or open, got $setting")
        }

        private fun liveKit(value: (String) -> String?): LiveKitConfig? {
            val names = listOf("LIVEKIT_PUBLIC_URL", "LIVEKIT_API_KEY", "LIVEKIT_API_SECRET")
            val values = names.map(value)
            if (values.all { it == null }) return null
            // Half a configuration is a deployment mistake, not a reason to quietly disable calls.
            require(values.none { it == null }) { "set all of ${names.joinToString()} or none" }
            val (url, key, secret) = values.filterNotNull()
            require(url.startsWith("wss://") || url.startsWith("ws://")) { "LIVEKIT_PUBLIC_URL must be a ws:// or wss:// address" }
            return LiveKitConfig(publicUrl = url, apiKey = key, apiSecret = secret)
        }

        private const val DEFAULT_PORT = 8080
        private const val DEFAULT_REQUEST_BURST = 300
        private const val DEFAULT_REQUESTS_PER_MINUTE = 600
        private const val DEFAULT_BURST = 20
        private const val DEFAULT_PER_MINUTE = 30
        private const val DEFAULT_POST_PER_MINUTE = 30
        private const val DEFAULT_CALL_PER_MINUTE = 30
        private const val DEFAULT_BOT_PER_MINUTE = 60
        private const val DEFAULT_BOT_API_PER_MINUTE = 300
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

data class LiveKitConfig(val publicUrl: String, val apiKey: String, val apiSecret: String) {
    override fun toString() = "LiveKitConfig(publicUrl=$publicUrl, apiKey=$apiKey)"
}

data class RateLimitConfig(val capacity: Int, val refillPerMinute: Int)

/** Who may create an account: only whoever brings an invite, or anyone. */
enum class RegistrationMode { Invite, Open }
