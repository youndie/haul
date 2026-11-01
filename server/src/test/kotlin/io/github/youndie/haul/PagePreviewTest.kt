package io.github.youndie.haul

import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.testing.CANVAS_NOW
import io.github.youndie.haul.testing.SAGA_CLOCK
import io.github.youndie.haul.testing.SeededDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a link to the stand shows before anyone opens it (B-161): the page the bundle ships, served as the stand serves
 * it, carries a title, a description and the Open Graph and Twitter card tags with one picture of the store, the same
 * at every storefront address; and `robots.txt` lets a crawler read it. The page is the real one
 * (`composeApp/src/wasmJsMain/resources`), not a stand-in.
 */
class PagePreviewTest {
    private val bundle = File(System.getProperty("haul.clientPage") ?: error("haul.clientPage is not set"))

    private fun served(block: suspend HttpClient.() -> Unit) =
        testApplication {
            application {
                haulModule(SeededDatabase.dataSource, CANVAS_NOW, commit = "test", web = bundle, sagaClock = SAGA_CLOCK)
            }
            client.block()
        }

    /** The page's `<meta>` tags, by their `name` or `property`. */
    private fun metas(page: String): Map<String, String> =
        Regex("""<meta (?:name|property)="([^"]+)" content="([^"]*)">""")
            .findAll(page)
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun `robots txt lets every crawler fetch everything`() =
        testApplication {
            // Answered by the server itself, bundle or not.
            application { haulModule(SeededDatabase.dataSource, CANVAS_NOW, commit = "test", sagaClock = SAGA_CLOCK) }
            val response = client.get("/robots.txt")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
            assertEquals(listOf("User-agent: *", "Allow: /"), response.bodyAsText().lines().filter { it.isNotBlank() })
        }

    @Test
    fun `the page says what the demo is, under the home's title`() =
        served {
            val page = get("/").bodyAsText()
            val title = Regex("<title>([^<]*)</title>").find(page)?.groupValues?.get(1)
            assertEquals(Frame.HOME_TITLE, title, "the page's <title> is not the home's tab title")
            val tags = metas(page)
            val description = assertNotNull(tags["description"], "no meta description")
            listOf("demo", "Kotlin", "Ktor", "kompot", "Compose Multiplatform").forEach {
                assertTrue(it in description, "the description does not say «$it»: $description")
            }
            assertEquals("website", tags["og:type"])
            assertEquals(STAND, tags["og:url"])
            assertEquals(title, tags["og:title"])
            assertEquals(description, tags["og:description"])
            assertEquals("summary_large_image", tags["twitter:card"])
            assertEquals(title, tags["twitter:title"])
            assertEquals(description, tags["twitter:description"])
        }

    /** The picture is the bundle's own, served from the stand's origin at the size the tags announce. */
    @Test
    fun `the preview's picture is a file the store serves, at the size it announces`() =
        served {
            val tags = metas(get("/").bodyAsText())
            val image = assertNotNull(tags["og:image"], "no og:image")
            assertEquals(image, tags["twitter:image"])
            assertTrue(image.startsWith(STAND), "the picture is not on the stand: $image")
            val response = get("/" + image.removePrefix(STAND))
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Image.PNG, response.contentType())
            val picture = ImageIO.read(response.bodyAsBytes().inputStream())
            assertEquals(tags["og:image:width"], picture.width.toString())
            assertEquals(tags["og:image:height"], picture.height.toString())
        }

    /** A shared product link previews as the store: the page is one file, whatever the address (B-36). */
    @Test
    fun `a storefront address carries the same preview`() =
        served {
            assertEquals(metas(get("/").bodyAsText()), metas(get("/p/p-sony-wh-1000xm6").bodyAsText()))
        }

    private companion object {
        const val STAND = "https://haul.kotlin.website/"
    }
}
