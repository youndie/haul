package io.github.youndie.haul.feature.catalog.data

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4 for S3, header form: the `Authorization` value of one request.
 *
 * S3's variant of the canonical request: the path is URI-encoded once, segment by segment, and never
 * normalised (`SigV4Test` holds it to AWS's own worked example). Only what [S3PhotoStore] sends is
 * covered: a path, no query string, the headers it names.
 */
internal class SigV4(
    private val accessKey: String,
    private val secretKey: String,
    private val region: String,
    private val service: String = "s3",
) {
    /**
     * The `Authorization` header for a request. [headers] are the headers to sign, `host`,
     * `x-amz-date` and `x-amz-content-sha256` among them; [amzDate] is `x-amz-date`'s value
     * (`yyyyMMdd'T'HHmmss'Z'`).
     */
    fun authorization(
        method: String,
        path: String,
        headers: Map<String, String>,
        payloadHash: String,
        amzDate: String,
    ): String {
        val signed = headers.mapKeys { it.key.lowercase() }.toSortedMap()
        val signedNames = signed.keys.joinToString(";")
        val scope = "${amzDate.take(DATE_LENGTH)}/$region/$service/aws4_request"
        val toSign =
            listOf(ALGORITHM, amzDate, scope, hex(sha256(canonicalRequest(method, path, signed, payloadHash))))
                .joinToString("\n")
        val signature = hex(hmac(signingKey(amzDate.take(DATE_LENGTH)), toSign))
        return "$ALGORITHM Credential=$accessKey/$scope, SignedHeaders=$signedNames, Signature=$signature"
    }

    /** The canonical request, exposed for the test that compares it with AWS's example. */
    internal fun canonicalRequest(
        method: String,
        path: String,
        headers: Map<String, String>,
        payloadHash: String,
    ): String {
        val sorted = headers.mapKeys { it.key.lowercase() }.toSortedMap()
        return listOf(
            method,
            encodePath(path),
            "",
            sorted.entries.joinToString("") { (name, value) -> "$name:${value.trim().replace(SPACES, " ")}\n" },
            sorted.keys.joinToString(";"),
            payloadHash,
        ).joinToString("\n")
    }

    private fun signingKey(date: String): ByteArray =
        listOf(region, service, "aws4_request")
            .fold(hmac("AWS4$secretKey".toByteArray(), date)) { key, part -> hmac(key, part) }

    companion object {
        private const val ALGORITHM = "AWS4-HMAC-SHA256"
        private const val DATE_LENGTH = 8
        private val SPACES = Regex(" +")
        private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        /** SHA-256 of [bytes] as lower-case hex: the `x-amz-content-sha256` of a body. */
        fun payloadHash(bytes: ByteArray): String = hex(sha256(bytes))

        /** S3's path encoding: every byte but the unreserved ones and `/`, as `%XX`; no normalisation. */
        fun encodePath(path: String): String =
            buildString {
                path.toByteArray().forEach { byte ->
                    val c = (byte.toInt() and BYTE).toChar()
                    if (c == '/' || c in UNRESERVED) append(c) else append("%%%02X".format(byte.toInt() and BYTE))
                }
            }

        private const val BYTE = 0xFF

        private fun sha256(text: String): ByteArray = sha256(text.toByteArray())

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun hmac(
            key: ByteArray,
            data: String,
        ): ByteArray =
            Mac
                .getInstance("HmacSHA256")
                .apply {
                    init(SecretKeySpec(key, "HmacSHA256"))
                }.doFinal(data.toByteArray())

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
