package io.github.youndie.haul.feature.catalog.data

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The signer against AWS's own worked example — «Example: GET Object» in the S3 API reference's
 * «Signature Calculations for the Authorization Header» — not against an expectation written from the
 * same reading of the specification the signer was written from.
 */
class SigV4Test {
    private val signer = SigV4("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1")
    private val emptyPayload = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val headers =
        mapOf(
            "Host" to "examplebucket.s3.amazonaws.com",
            "Range" to "bytes=0-9",
            "x-amz-content-sha256" to emptyPayload,
            "x-amz-date" to "20130524T000000Z",
        )

    @Test
    fun `the canonical request is AWS's`() {
        val canonical = signer.canonicalRequest("GET", "/test.txt", headers, emptyPayload)
        val hash =
            MessageDigest
                .getInstance(
                    "SHA-256",
                ).digest(canonical.toByteArray())
                .joinToString("") { "%02x".format(it) }
        assertEquals("7344ae5b7ee6c3e7e6b0fe0640412a37625d1fbfff95c48bbb2dc43964946972", hash, canonical)
    }

    @Test
    fun `the signature is AWS's`() {
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, " +
                "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, " +
                "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41",
            signer.authorization("GET", "/test.txt", headers, emptyPayload, "20130524T000000Z"),
        )
    }

    /** S3 encodes a key once and never normalises it: `$` and a space are escaped, `/` is not. */
    @Test
    fun `a key is encoded per segment and not normalised`() {
        assertEquals("/b/test%24file%20a.txt", SigV4.encodePath("/b/test\$file a.txt"))
        assertEquals("/b/a/../c", SigV4.encodePath("/b/a/../c"))
    }
}
