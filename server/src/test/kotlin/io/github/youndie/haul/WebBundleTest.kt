package io.github.youndie.haul

import io.github.youndie.haul.testing.BROWSER_ENCODINGS
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.ERROR_ANSWERS_LOG
import io.github.youndie.haul.testing.SAGA_CLOCK
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.capturingLog
import io.github.youndie.haul.testing.rawGet
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.fullPath
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The browser bundle served beside the API by the one process (haul-web, B-27; compressed, B-34; the
 * page at the storefront's addresses, B-36).
 */
class WebBundleTest {
    /** What the distribution looks like once the image has precompressed it (`docker/Dockerfile`). */
    private val bundle: File =
        Files.createTempDirectory("haul-web").toFile().apply {
            resolve("index.html").writeText("""<script src="composeApp.js"></script>""")
            resolve("index.html.br").writeBytes(PAGE_BR)
            resolve(MODULE).writeBytes(WASM)
            resolve("$MODULE.br").writeBytes(WASM_BR)
            resolve("$MODULE.gz").writeBytes(WASM_GZ)
            resolve("composeApp.js").writeText("// the loader")
            // A bundle that still carries its map: the server must not publish it either.
            resolve("composeApp.js.map").writeText("{}")
        }

    private fun withBundle(block: suspend HttpClient.() -> Unit) =
        testApplication {
            application {
                haulModule(
                    SeededDatabase.dataSource,
                    CANVAS_NOW,
                    commit = "test",
                    web = bundle,
                    sagaClock = SAGA_CLOCK,
                )
            }
            client.block()
        }

    private suspend fun HttpClient.module(acceptEncoding: String?): HttpResponse =
        get("/$MODULE") { acceptEncoding?.let { header(HttpHeaders.AcceptEncoding, it) } }

    private suspend fun HttpResponse.errorBody(): ErrorBody =
        haulWireJson.decodeFromString(ErrorBody.serializer(), bodyAsText())

    private fun HttpResponse.varies(): Boolean =
        headers.getAll(HttpHeaders.Vary).orEmpty().any { HttpHeaders.AcceptEncoding in it }

    @Test
    fun `the root answers the page`() =
        withBundle {
            val response = get("/")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue("composeApp.js" in response.bodyAsText())
        }

    /** The browser compiles a module streamed only when it is labelled `application/wasm`. */
    @Test
    fun `the wasm module is served as application wasm`() =
        withBundle {
            val response = module(acceptEncoding = null)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType("application", "wasm"), response.contentType()?.withoutParameters())
        }

    @Test
    fun `a browser gets the brotli file, still labelled wasm, varying on the encoding`() =
        withBundle {
            val response = module(BROWSER)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("br", response.headers[HttpHeaders.ContentEncoding])
            assertContentEquals(WASM_BR, response.bodyAsBytes())
            assertEquals(ContentType("application", "wasm"), response.contentType()?.withoutParameters())
            assertTrue(response.varies(), "Vary: ${response.headers.getAll(HttpHeaders.Vary)}")
        }

    @Test
    fun `a client that accepts only gzip gets the gzip file`() =
        withBundle {
            val response = module("gzip")

            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
            assertContentEquals(WASM_GZ, response.bodyAsBytes())
            assertEquals(ContentType("application", "wasm"), response.contentType()?.withoutParameters())
        }

    /** A cache that stored this answer without Vary would hand the raw module to every browser. */
    @Test
    fun `a client that accepts no encoding gets the file as it is, varying on the encoding too`() =
        withBundle {
            val response = module(acceptEncoding = null)

            assertNull(response.headers[HttpHeaders.ContentEncoding])
            assertContentEquals(WASM, response.bodyAsBytes())
            assertTrue(response.varies(), "Vary: ${response.headers.getAll(HttpHeaders.Vary)}")
        }

    @Test
    fun `a module named by its content hash is cached for a year`() =
        withBundle {
            assertEquals(IMMUTABLE, module(BROWSER).headers[HttpHeaders.CacheControl])
            assertEquals(IMMUTABLE, module(acceptEncoding = null).headers[HttpHeaders.CacheControl])
        }

    /** Their names survive a release, so the next visit must ask again. */
    @Test
    fun `the page and the files that keep their names are not cached blindly`() =
        withBundle {
            assertEquals(
                "no-cache",
                get("/") { header(HttpHeaders.AcceptEncoding, BROWSER) }.headers[HttpHeaders.CacheControl],
            )
            assertEquals("no-cache", get("/composeApp.js").headers[HttpHeaders.CacheControl])
        }

    @Test
    fun `the source map is not served`() =
        withBundle {
            assertEquals(HttpStatusCode.NotFound, get("/composeApp.js.map").status)
        }

    @Test
    fun `a screen route still answers its tree beside the bundle`() =
        withBundle {
            val response = get("/ui/home") { header(HttpHeaders.AcceptEncoding, BROWSER) }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        }

    /** No fallback to the page: an unknown path stays a 404 the client can draw, not a 200 page. */
    @Test
    fun `a missing file is a 404, not the page`() =
        withBundle {
            assertEquals(
                HttpStatusCode.NotFound,
                get("/ui/nowhere") { header(HttpHeaders.AcceptEncoding, BROWSER) }.status,
            )
            assertEquals(HttpStatusCode.NotFound, get("/no-such-file.js").status)
        }

    /** A reloaded, pasted or shared storefront address opens the page, which then draws it (B-36). */
    @Test
    fun `a storefront address answers the page, not cached blindly and varying on the encoding`() =
        withBundle {
            STOREFRONT.forEach { address ->
                val response = get(address)

                assertEquals(HttpStatusCode.OK, response.status, address)
                assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters(), address)
                assertTrue("composeApp.js" in response.bodyAsText(), address)
                assertEquals("no-cache", response.headers[HttpHeaders.CacheControl], address)
                assertTrue(response.varies(), "$address: Vary ${response.headers.getAll(HttpHeaders.Vary)}")
            }
        }

    @Test
    fun `a browser gets the page at a storefront address precompressed, as at the root`() =
        withBundle {
            listOf("/", "/p/p-001-05", "/c/electronics/audio/headphones").forEach { address ->
                val response = get(address) { header(HttpHeaders.AcceptEncoding, BROWSER) }

                assertEquals(HttpStatusCode.OK, response.status, address)
                assertEquals("br", response.headers[HttpHeaders.ContentEncoding], address)
                assertContentEquals(PAGE_BR, response.bodyAsBytes(), address)
                assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters(), address)
                assertEquals("no-cache", response.headers[HttpHeaders.CacheControl], address)
            }
        }

    /**
     * A category has one address (B-68): the last slug alone, as a link from before it or a hand-typed address
     * names it, is redirected to the page at the whole path, query kept, so the client starts at the address
     * every link and filter of the page names.
     */
    @Test
    fun `another form of a category address arrives at the page at its one address`() =
        withBundle {
            val response = get("/c/headphones?brand=Sony") { header(HttpHeaders.AcceptEncoding, BROWSER) }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("/c/electronics/audio/headphones?brand=Sony", response.request.url.fullPath)
            assertContentEquals(PAGE_BR, response.bodyAsBytes())
        }

    /** An allow-list, not a catch-all (B-27): anything that is neither a file nor a storefront address. */
    @Test
    fun `a path that is no storefront address stays a 404`() =
        withBundle {
            NOT_STOREFRONT.forEach { path ->
                assertEquals(
                    HttpStatusCode.NotFound,
                    get(path) { header(HttpHeaders.AcceptEncoding, BROWSER) }.status,
                    path,
                )
            }
        }

    /**
     * A storefront address whose query does not decode is still the page (B-117): the tree under `/ui` refuses
     * the query naming no parameter — what the client draws as «This link is out of date» — and an API route that
     * reads its query refuses it too. Before, the page request itself answered the API's JSON, shown as the page.
     * Sent over a socket to the engine the stand runs: Ktor's own client decodes a query before it sends one.
     */
    @Test
    fun `a storefront address whose query does not decode still answers the page, its tree refuses it`() =
        onCio { port ->
            MALFORMED.forEach { address ->
                val page = rawGet(port, address)

                assertEquals(HttpStatusCode.OK.value, page.status, address)
                assertEquals(ContentType.Text.Html, page.contentType, address)
                assertTrue("composeApp.js" in page.body, address)
                assertEquals("no-cache", page.headers[HttpHeaders.CacheControl.lowercase()], address)

                val tree = rawGet(port, "/ui$address")

                assertEquals(HttpStatusCode.BadRequest.value, tree.status, address)
                assertEquals(ContentType.Application.Json, tree.contentType, address)
                val refusal = haulWireJson.decodeFromString(ErrorBody.serializer(), tree.body)
                assertEquals(ErrorCode.ValidationFailed, refusal.code, address)
                assertNull(refusal.field, address)
            }
            val api = rawGet(port, "$QUERY_READER?%zz")

            assertEquals(HttpStatusCode.BadRequest.value, api.status)
            assertEquals(ContentType.Application.Json, api.contentType)
            assertEquals(
                ErrorCode.ValidationFailed,
                haulWireJson.decodeFromString(ErrorBody.serializer(), api.body).code,
            )
        }

    /**
     * The page is where such a call ends (B-148). Ktor's routing ran after the page had gone and decoded the query
     * as it resolved the call: the catch-all logged a `400` for an address answered `200`, and on the stand katcher
     * filed each guest's mangled link as a group.
     */
    @Test
    fun `a storefront address whose query does not decode ends with the page, no failure behind it`() =
        capturingLog(ERROR_ANSWERS_LOG) { lines ->
            onCio { port ->
                MALFORMED.forEach { address ->
                    assertEquals(HttpStatusCode.OK.value, rawGet(port, address).status, address)
                }
            }
            assertTrue(lines.isEmpty(), "the catch-all met: ${lines.map { it.formattedMessage }}")
        }

    /** The application with the bundle on CIO, the stand's engine, on a free port, for [block]. */
    private fun onCio(block: (port: Int) -> Unit) {
        val server =
            embeddedServer(CIO, port = 0) {
                haulModule(SeededDatabase.dataSource, CANVAS_NOW, commit = "test", web = bundle, sagaClock = SAGA_CLOCK)
                // An API route that reads its query, as any would: the public `GET`s under `/api` read none today.
                routing { get(QUERY_READER) { call.respondText(call.request.queryParameters.toString()) } }
            }.start(wait = false)
        try {
            block(
                runBlocking {
                    server.engine
                        .resolvedConnectors()
                        .first()
                        .port
                },
            )
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    @Test
    fun `the bundle's own files are still the files`() =
        withBundle {
            assertEquals("// the loader", get("/composeApp.js").bodyAsText())
            assertContentEquals(WASM, module(acceptEncoding = null).bodyAsBytes())
        }

    private companion object {
        /** webpack's name for an emitted wasm: twenty hex digits of its content hash. */
        const val MODULE = "bfa5198fb2fe683c613a.wasm"
        const val BROWSER = BROWSER_ENCODINGS
        const val IMMUTABLE = "public, max-age=31536000, immutable"
        const val QUERY_READER = "/api/v1/test/query"

        /** Every shape of `StorefrontPage`, as the server's `NavigateAction`s write them. */
        val STOREFRONT =
            listOf(
                "/c",
                "/c/electronics",
                "/c/electronics/audio/headphones?brand=Sony&feature=Noise%20cancelling",
                "/p/p-001-05",
                "/p/p-001-05?sku=s-1&tab=specifications",
                "/search?q=running%20shoes",
                "/cart",
                "/deals",
                "/deals?page=2",
                "/checkout",
                "/account",
                "/account/orders",
                "/account/orders?status=active",
                "/account/orders/HL-48302",
                "/account/saved",
                "/account/saved?filter=price-dropped&page=2",
                "/sign-in",
                "/about",
                "/live",
            )

        /** The run's four (TC-ERR-04 step 4): a value cut off mid-escape, a name and values that are no escape. */
        val MALFORMED =
            listOf(
                "/c/electronics?brand=%E0%A4%A",
                "/search?q=%zz",
                "/c/electronics?%zz",
                "/p/p-sony-wh-1000xm6?sku=%zz",
            )

        val NOT_STOREFRONT =
            listOf(
                "/nowhere",
                "/ui/nowhere",
                "/api/nowhere",
                "/images/nowhere.webp",
                "/deals/today",
                "/c/",
                "/c/headphones/",
                "/p/",
                "/p/p-001-05/reviews",
                "/search/extra",
                "/cart/1",
                "/saved",
                "/p/p-001-05.map",
            )
        val WASM = byteArrayOf(0, 0x61, 0x73, 0x6d, 1, 0, 0, 0)
        val WASM_BR = byteArrayOf(0x0b, 0x03, 0x80.toByte())
        val WASM_GZ = byteArrayOf(0x1f, 0x8b.toByte(), 8, 0)
        val PAGE_BR = byteArrayOf(0x1b, 0x27, 0x00)
    }
}
