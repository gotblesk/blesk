package app.blesk.server

import app.blesk.server.attachments.SigV4Signer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SigV4SignerTest {
    /** The "GET Object" example from the AWS Signature Version 4 documentation for S3. */
    @Test
    fun `matches the published AWS example`() {
        val signer = SigV4Signer("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
        val emptyHash = SigV4Signer.sha256Hex(ByteArray(0))
        val authorization = signer.authorization(
            method = "GET",
            canonicalUri = "/test.txt",
            canonicalQuery = "",
            headers = mapOf(
                "Host" to "examplebucket.s3.amazonaws.com",
                "Range" to "bytes=0-9",
                "x-amz-content-sha256" to emptyHash,
                "x-amz-date" to "20130524T000000Z",
            ),
            payloadHash = emptyHash,
            time = Instant.parse("2013-05-24T00:00:00Z"),
        )
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request," +
                "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date," +
                "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41",
            authorization,
        )
    }
}
