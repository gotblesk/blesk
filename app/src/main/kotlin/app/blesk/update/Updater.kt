package app.blesk.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.blesk.BuildConfig
import app.blesk.core.network.NetworkProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** A newer build on GitHub Releases: its number, title, notes and the APK to fetch. */
data class Update(
    val versionCode: Int,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    /** SHA-256 of the APK as published in the release notes; null when they do not give one. */
    val sha256: String? = null,
)

/** The APK's SHA-256 from the line the release workflow writes into the notes, lowercase. */
internal fun publishedChecksum(notes: String): String? = CHECKSUM.find(notes)?.groupValues?.get(1)?.lowercase()

private val CHECKSUM = Regex("""SHA-256 APK: `?([0-9a-fA-F]{64})`?""")

/** How fetching an update went. */
enum class UpdateFailure { Network, Signature, Checksum }

/**
 * Updates from the project's GitHub Releases, outside any store. The APK is installed only if its
 * SHA-256 matches the one in the release notes and it is signed with the same key as the running
 * app; Android checks the signature again when installing.
 * GitHub sees the phone's address when it is asked, through the proxy if there is one.
 */
class Updater(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .proxySelector(NetworkProxy.selector)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val found = MutableStateFlow<Update?>(null)

    /** The newest build above this one, once a check found it. */
    val available: StateFlow<Update?> = found.asStateFlow()

    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(RELEASES).header("Accept", "application/vnd.github+json").build()
        val releases = runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                json.decodeFromString<List<Release>>(response.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
        val newest = releases.mapNotNull { it.toUpdate() }.maxByOrNull { it.versionCode }
        newest?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }.also { found.value = it }
    }

    /** Downloads [update] with [progress] from 0 to 1 and opens the system installer; null when it is on its way. */
    suspend fun install(update: Update, progress: (Float) -> Unit): UpdateFailure? = withContext(Dispatchers.IO) {
        val apk = File(File(context.cacheDir, "update").apply { mkdirs() }, "blesk.apk")
        try {
            download(update, apk, progress)
        } catch (_: IOException) {
            apk.delete()
            return@withContext UpdateFailure.Network
        }
        // Checked first: a file that is not the published one is not opened by anything at all.
        if (update.sha256 == null || sha256(apk) != update.sha256) {
            apk.delete()
            return@withContext UpdateFailure.Checksum
        }
        if (!signedLikeThisApp(apk)) {
            apk.delete()
            return@withContext UpdateFailure.Signature
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        null
    }

    private fun download(update: Update, target: File, progress: (Float) -> Unit) {
        http.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
            val body = response.body?.takeIf { response.isSuccessful } ?: throw IOException("HTTP ${response.code}")
            val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
            body.byteStream().use { input -> target.outputStream().use { output -> copy(input, output, total, progress) } }
        }
    }

    private fun copy(input: InputStream, output: OutputStream, total: Long, progress: (Float) -> Unit) {
        val buffer = ByteArray(BUFFER)
        var done = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            done += read
            if (total > 0) progress((done.toFloat() / total).coerceAtMost(1f))
        }
    }

    private fun signedLikeThisApp(apk: File): Boolean {
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(apk.path, SigningKeys.FLAGS) ?: return false
        val installed = manager.getPackageInfo(context.packageName, SigningKeys.FLAGS)
        if (archive.packageName != context.packageName) return false
        val expected = SigningKeys.fingerprints(installed)
        return expected.isNotEmpty() && SigningKeys.fingerprints(archive) == expected
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tag: String,
        val name: String? = null,
        val body: String? = null,
        val draft: Boolean = false,
        val assets: List<Asset> = emptyList(),
    ) {
        fun toUpdate(): Update? {
            if (draft) return null
            val code = tag.removePrefix(TAG_PREFIX).toIntOrNull() ?: return null
            val apk = assets.firstOrNull { it.name == APK_NAME } ?: return null
            val notes = body.orEmpty()
            return Update(code, name ?: tag, notes, apk.url, apk.size, publishedChecksum(notes))
        }
    }

    @Serializable
    private data class Asset(val name: String, @SerialName("browser_download_url") val url: String, val size: Long = 0)

    private companion object {
        const val RELEASES = "https://api.github.com/repos/gotblesk/blesk/releases?per_page=10"
        const val TAG_PREFIX = "build-"
        const val APK_NAME = "blesk.apk"
        const val APK_TYPE = "application/vnd.android.package-archive"
        const val TIMEOUT_SECONDS = 120L
        const val BUFFER = 64 * 1024
    }
}
