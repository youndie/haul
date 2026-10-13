package io.github.youndie.haul.testing

import io.ktor.http.ContentType
import java.net.Socket

/**
 * What a browser sends for [address], byte for byte, as a browser asking for a page would — over a socket to a
 * server on [port], because Ktor's own client decodes a query before it sends one and cannot send `?q=%zz` (B-117).
 */
internal fun rawGet(
    port: Int,
    address: String,
): RawResponse =
    Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 10_000
        val request =
            "GET $address HTTP/1.1\r\nHost: haul\r\nAccept-Encoding: $BROWSER_ENCODINGS\r\nConnection: close\r\n"
        socket.getOutputStream().write("$request\r\n".toByteArray())
        val (head, body) =
            socket
                .getInputStream()
                .readBytes()
                .decodeToString()
                .split("\r\n\r\n", limit = 2)
        val (status, headers) = head.split("\r\n").let { it.first() to it.drop(1) }
        RawResponse(
            status = status.split(' ')[1].toInt(),
            headers = headers.associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() },
            body = body,
        )
    }

internal class RawResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
) {
    val contentType: ContentType? get() = headers["content-type"]?.let { ContentType.parse(it).withoutParameters() }
}

/** The encodings a current browser accepts, in its order. */
internal const val BROWSER_ENCODINGS = "gzip, deflate, br, zstd"
