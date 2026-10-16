package app.blesk.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** Which key this copy of blesk is signed with, as far as the published keys go. */
enum class BuildOrigin { Official, Test, Unofficial }

/** The running app's signing key: its SHA-256 and what that says about where the build came from. */
data class Authenticity(val origin: BuildOrigin, val fingerprint: String)

/**
 * Signing keys of APKs. The published fingerprints are the ones in `README.md`: a build signed with
 * anything else is not ours, however it looks. The test key lives in the repository, so anyone can
 * sign with it; such a build is honestly called a test build.
 */
object SigningKeys {
    /** The owner's release key, once it exists; see `docs/NEEDS_HUMAN.md`, item 5. */
    private val OFFICIAL = emptySet<String>()
    private const val TEST = "bd7e78024ea47ca7e7de70c02c39beff5a2855be35560288cdd477e57f9d78bd"

    fun of(context: Context): Authenticity {
        val info = context.packageManager.getPackageInfo(context.packageName, FLAGS)
        val fingerprint = fingerprints(info).singleOrNull().orEmpty()
        val origin = when (fingerprint) {
            in OFFICIAL -> BuildOrigin.Official
            TEST -> BuildOrigin.Test
            else -> BuildOrigin.Unofficial
        }
        return Authenticity(origin, fingerprint)
    }

    /** SHA-256 of each signing certificate, lowercase hex, as `apksigner --print-certs` shows them. */
    fun fingerprints(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    val FLAGS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
}
