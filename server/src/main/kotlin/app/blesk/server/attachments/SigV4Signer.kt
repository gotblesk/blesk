package app.blesk.server.attachments

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4 request signing, which MinIO requires on its S3 API. This authenticates
 * requests to our own storage; it has nothing to do with message encryption.
 */
class SigV4Signer(
    private val accessKey: String,
    private val secretKey: String,
    private val region: String = "us-east-1",
    private val service: String = "s3",
) {
    /**
     * Returns the Authorization header value. [headers] are the headers to sign (they must include
     * `host`, `x-amz-date` and `x-amz-content-sha256`); [canonicalUri] is already URI-encoded.
     */
    fun authorization(
        method: String,
        canonicalUri: String,
        canonicalQuery: String,
        headers: Map<String, String>,
        payloadHash: String,
        time: Instant,
    ): String {
        val sorted = headers.mapKeys { it.key.lowercase() }.toSortedMap()
        val signedHeaders = sorted.keys.joinToString(";")
        val canonicalHeaders = sorted.entries.joinToString("") { (name, value) -> "$name:${value.trim()}\n" }
        val canonicalRequest = listOf(method, canonicalUri, canonicalQuery, canonicalHeaders, signedHeaders, payloadHash)
            .joinToString("\n")

        val date = DATE.format(time)
        val scope = "$date/$region/$service/aws4_request"
        val stringToSign = listOf(ALGORITHM, timestamp(time), scope, sha256Hex(canonicalRequest.encodeToByteArray()))
            .joinToString("\n")

        val signingKey = listOf(date, region, service, "aws4_request")
            .fold("AWS4$secretKey".encodeToByteArray()) { key, part -> hmac(key, part) }
        val signature = hmac(signingKey, stringToSign).toHex()
        return "$ALGORITHM Credential=$accessKey/$scope,SignedHeaders=$signedHeaders,Signature=$signature"
    }

    companion object {
        const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
        private const val ALGORITHM = "AWS4-HMAC-SHA256"
        private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
        private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

        fun timestamp(time: Instant): String = TIMESTAMP.format(time)

        fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

        private fun hmac(key: ByteArray, data: String): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data.encodeToByteArray())
        }

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
