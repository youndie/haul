package io.github.youndie.haul.feature.about

import io.github.youndie.haul.StorefrontPage
import io.github.youndie.haul.feature.about.screen.AboutScreen
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.AboutBody
import io.github.youndie.haul.ui.DemoNote
import io.github.youndie.haul.ui.HaulFooter
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.encodeKompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.OpenUrlAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-160: the stand says it is a demo built with Kotlin, and where its code is. The strip is a tree of its own, which
 * the storefront asks for apart from any page; the About page is the frame around what the stack is, what is real and
 * what is not, at an address a reload opens.
 */
class AboutRoutesTest {
    private val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))

    @Test
    fun `the demo strip says the stand is a Kotlin demo and leads to the about page and the repository`() =
        haulTest {
            val note = tree("/ui/demo")
            assertTrue(note is DemoNote, "the strip is a tree of its own: $note")
            assertTrue("demo" in note.text && "Kotlin" in note.text, note.text)
            val links = note.links.associate { it.label to it.action }
            assertEquals(NavigateAction("/about"), links["About this demo"])
            assertEquals(OpenUrlAction("https://github.com/youndie/haul"), links["Source on GitHub"])
            assertEquals(StorefrontPage.About, StorefrontPage.of("/about"), "a reload of the strip's link is no page")
            // The panel's toggle is named by the server; the panel itself is the client's (B-163).
            assertEquals("Under the hood", note.hood)
        }

    @Test
    fun `the about page is the frame around the stack what is real and what is not`() =
        haulTest {
            val page = tree("/ui/about")
            assertEquals("about-page", page.id)
            assertEquals(
                listOf("header", "about", "footer"),
                (page as ColumnComponent).children.map { it.id },
                "the header, the body and the footer, as every page with a footer is framed",
            )
            // The header alone names no page (B-161); on a page it carries the page's tab title, and is otherwise the same.
            val header = page.only<HaulHeader>()
            assertEquals("About this demo · Haul", header.tabTitle)
            assertEquals(tree("/ui/header"), header.copy(tabTitle = null), "the header is every page's")
            assertEquals(tree("/ui/home").only<HaulFooter>(), page.only<HaulFooter>(), "the footer is left as it is")
            val about = page.only<AboutBody>()
            assertEquals(
                listOf("What it is built with", "What is real", "What is not"),
                about.sections.map { it.title },
            )
            val stack =
                about.sections
                    .first()
                    .points
                    .joinToString(" ") { "${it.name} ${it.text}" }
            listOf("Ktor", "zavarnik", "kompot", "petich", "shildik", "Compose Multiplatform", "shoppers", "tracy")
                .forEach { assertTrue(it in stack, "the stack does not name $it: $stack") }
            val real = about.sections[1].points.map { it.name }
            assertEquals(
                listOf("The saga and its compensations", "The sellers, couriers and payments", "The observability"),
                real,
            )
            val not = about.sections[2].points.joinToString(" ") { "${it.name} ${it.text}" }
            listOf("No money moves", "No parcel ships", "Invented").forEach { assertTrue(it in not, not) }
            assertEquals(
                listOf(
                    OpenUrlAction("https://github.com/youndie/haul"),
                    OpenUrlAction("https://github.com/youndie/haul#readme"),
                ),
                about.links.map { it.action },
            )
        }

    /**
     * The goldens draw the strip and the About page from wire bodies (`composeApp/src/desktopTest/resources/bodies`);
     * this holds the strip's and the body's to what the server sends. The page around the body is the guest home's
     * header and footer, held by their own tests.
     */
    @Test
    fun `the strip's and the about page's bodies are what the server sends`() {
        assertEquals(
            Json.parseToJsonElement(haulWireJson.encodeKompotComponent(AboutScreen.note())),
            Json.parseToJsonElement(File(bodies, "demo_note.json").readText()),
        )
        val drawn =
            Json
                .parseToJsonElement(File(bodies, "about_content.json").readText())
                .jsonObject
                .getValue("children")
                .jsonArray
                .single { it.jsonObject["type"] == JsonPrimitive("haul_about") }
        assertEquals(Json.parseToJsonElement(haulWireJson.encodeKompotComponent(AboutScreen.body())), drawn)
    }
}
