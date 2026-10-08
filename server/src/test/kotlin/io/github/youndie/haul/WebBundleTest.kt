package io.github.youndie.haul

import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.SeededDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The browser bundle served beside the API by the one process (haul-web, B-27). */
class WebBundleTest {
    private val bundle: File =
        Files.createTempDirectory("haul-web").toFile().apply {
            resolve("index.html").writeText("""<script src="composeApp.js"></script>""")
            resolve("composeApp.wasm").writeBytes(byteArrayOf(0, 0x61, 0x73, 0x6d))
        }

    private fun withBundle(block: suspend HttpClient.() -> Unit) =
        testApplication {
            application { haulModule(SeededDatabase.dataSource, CANVAS_NOW, commit = "test", web = bundle) }
            client.block()
        }

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
            val response = get("/composeApp.wasm")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType("application", "wasm"), response.contentType()?.withoutParameters())
        }

    @Test
    fun `a screen route still answers its tree beside the bundle`() =
        withBundle {
            val response = get("/ui/home")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        }

    /** No fallback to the page: an unknown path stays a 404 the client can draw, not a 200 page. */
    @Test
    fun `a missing file is a 404, not the page`() =
        withBundle {
            assertEquals(HttpStatusCode.NotFound, get("/ui/nowhere").status)
            assertEquals(HttpStatusCode.NotFound, get("/no-such-file.js").status)
        }
}
