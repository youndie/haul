package io.github.youndie.haul.feature.catalog.data

import io.github.youndie.haul.feature.catalog.domain.PhotoStore
import io.github.youndie.haul.feature.catalog.domain.StoredPhoto
import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** An S3-compatible bucket the photos live in, addressed path-style: `<endpoint>/<bucket>/<key>`. */
internal data class S3Config(
    val endpoint: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val region: String = DEFAULT_REGION,
) {
    override fun toString(): String = "S3Config(endpoint=$endpoint, bucket=$bucket, region=$region)"

    companion object {
        const val DEFAULT_REGION = "us-east-1"
    }
}

/** The store refused or could not be reached; the message names the request and the answer. */
internal class PhotoStoreException(
    message: String,
) : RuntimeException(message)

/**
 * [PhotoStore] over S3's REST API: `PUT` and `GET` of one object, signed with [SigV4].
 *
 * The JDK's own `java.net.http.HttpClient` rather than an SDK: the server needs two calls on one
 * bucket, the JDK already carries an HTTP client, and AWS SDK v2 would bring a dozen jars and an HTTP
 * stack of its own into the image and its AOT cache for them (B-30 findings). Path-style addressing,
 * because SeaweedFS, MinIO-likes and a bucket name with dots all accept it, and a virtual-hosted
 * name does not resolve for `127.0.0.1`.
 *
 * [now] is the clock the signature is dated by: the store compares it with its own, so the
 * composition root hands in the wall clock (S3 refuses a request more than 15 minutes off).
 */
internal class S3PhotoStore(
    private val config: S3Config,
    private val now: () -> Instant,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
) : PhotoStore {
    private val signer = SigV4(config.accessKey, config.secretKey, config.region)

    override suspend fun put(
        key: String,
        bytes: ByteArray,
        contentType: String,
    ) {
        val response =
            send("PUT", key, bytes, HttpResponse.BodyHandlers.ofString()) {
                header("Content-Type", contentType)
            }
        if (response.statusCode() !in SUCCESS) throw refused("PUT", key, response.statusCode(), response.body())
    }

    override suspend fun get(key: String): StoredPhoto? {
        val response = send("GET", key, EMPTY, HttpResponse.BodyHandlers.ofByteArray()) {}
        return when (response.statusCode()) {
            in SUCCESS -> {
                StoredPhoto(
                    response.body(),
                    response.headers().firstValue("Content-Type").orElse(OCTET_STREAM),
                )
            }

            NOT_FOUND -> {
                null
            }

            else -> {
                throw refused("GET", key, response.statusCode(), response.body().decodeToString())
            }
        }
    }

    private suspend fun <T> send(
        method: String,
        key: String,
        body: ByteArray,
        handler: HttpResponse.BodyHandler<T>,
        extra: HttpRequest.Builder.() -> Unit,
    ): HttpResponse<T> {
        val path = "/${config.bucket}/$key"
        val uri = URI.create(config.endpoint.trimEnd('/') + SigV4.encodePath(path))
        val amzDate = AMZ_DATE.format(now())
        val payload = SigV4.payloadHash(body)
        val signed =
            mapOf(
                "host" to hostHeader(uri),
                "x-amz-content-sha256" to payload,
                "x-amz-date" to amzDate,
            )
        val request =
            HttpRequest
                .newBuilder(uri)
                .timeout(TIMEOUT)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
                .header("x-amz-content-sha256", payload)
                .header("x-amz-date", amzDate)
                .header("Authorization", signer.authorization(method, path, signed, payload, amzDate))
                .apply(extra)
                .build()
        return try {
            http.sendAsync(request, handler).await()
        } catch (e: java.io.IOException) {
            throw PhotoStoreException("$method ${config.bucket}/$key: ${e.message ?: e::class.simpleName}")
        }
    }

    private fun refused(
        method: String,
        key: String,
        status: Int,
        body: String,
    ) = PhotoStoreException("$method ${config.bucket}/$key answered $status: ${body.take(BODY_SHOWN)}")

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(10)
        val SUCCESS = 200..299
        const val NOT_FOUND = 404
        const val BODY_SHOWN = 300
        const val OCTET_STREAM = "application/octet-stream"
        val EMPTY = ByteArray(0)
        val AMZ_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

        /** The `Host` the JDK client sends, which is what has to be signed: the port only when it is not the scheme's own. */
        fun hostHeader(uri: URI): String {
            val defaultPort = if (uri.scheme == "https") HTTPS_PORT else HTTP_PORT
            return if (uri.port == -1 || uri.port == defaultPort) uri.host else "${uri.host}:${uri.port}"
        }

        const val HTTP_PORT = 80
        const val HTTPS_PORT = 443
    }
}
