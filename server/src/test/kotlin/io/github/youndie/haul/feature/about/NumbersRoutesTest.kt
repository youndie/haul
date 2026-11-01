package io.github.youndie.haul.feature.about

import io.github.youndie.haul.StorefrontPage
import io.github.youndie.haul.feature.about.domain.StackNumbers
import io.github.youndie.haul.feature.about.screen.NumbersScreen
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.AboutBody
import io.github.youndie.haul.ui.HaulFooter
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.encodeKompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.OpenUrlAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-173: «The stack in numbers» is a server tree in the frame, drawn as the About page is and reached from it — and
 * every number it writes is one the measurement's reader wrote into the data file from the raw runs
 * (`numbers/stack-numbers.json`, held to `docs/research/measurements/b-173/raw/` by `make check`). A number typed
 * into the copy, or one the data file does not hold, fails here.
 */
class NumbersRoutesTest {
    private val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))

    @Test
    fun `the numbers page is the frame around what the server was measured to cost`() =
        haulTest {
            val page = tree("/ui/numbers")
            assertEquals("numbers-page", page.id)
            assertEquals(
                listOf("header", "numbers", "footer"),
                (page as ColumnComponent).children.map { it.id },
                "the header, the body and the footer, as the About page is framed",
            )
            val header = page.only<HaulHeader>()
            assertEquals("The stack in numbers · Haul", header.tabTitle)
            assertEquals(tree("/ui/header"), header.copy(tabTitle = null), "the header is every page's")
            assertEquals(tree("/ui/home").only<HaulFooter>(), page.only<HaulFooter>(), "the footer is left as it is")
            val body = page.only<AboutBody>()
            assertEquals(
                listOf("Start-up", "Latency under load", "CPU per request", "Memory", "How it was measured"),
                body.sections.map { it.title },
            )
            assertEquals(
                listOf("The home page", "A category", "A product", "Search suggestions", "Live orders"),
                body.sections[1]
                    .points
                    .map { it.name }
                    .drop(1)
                    .dropLast(1),
                "every route the load asked for, between the load and the verdict",
            )
            val numbers = StackNumbers.measured
            assertTrue(
                numbers.measured in body.lead && numbers.commit.take(7) in body.lead,
                "the day and the commit: ${body.lead}",
            )
            assertTrue(
                body.sections
                    .last()
                    .points
                    .any { "scripts/measure-stack.py" in it.text },
                "the page says how to measure it again",
            )
            assertEquals(
                listOf(
                    OpenUrlAction("https://github.com/youndie/haul/tree/main/docs/research/measurements/b-173"),
                    NavigateAction("/about"),
                ),
                body.links.map { it.action },
            )
            assertEquals(StorefrontPage.Numbers, StorefrontPage.of("/numbers"), "a reload of the page is no page")
        }

    @Test
    fun `the about page leads to the numbers`() =
        haulTest {
            val about = tree("/ui/about").only<AboutBody>()
            assertTrue(about.links.any { it.action == NavigateAction("/numbers") }, "${about.links}")
        }

    /**
     * Every number the page writes is a value of the data file, as the page renders values: as it is, to a tenth,
     * microseconds as milliseconds to a tenth, or a share as a whole percent. The day and the commit are taken out
     * first; nothing else on the page may hold a digit the data does not.
     */
    @Test
    fun `every number on the page is one the measurement wrote`() {
        val data = Json.parseToJsonElement(resource(StackNumbers.RESOURCE))
        val numbers = StackNumbers.measured
        val renderings = leaves(data).flatMap { renderings(it) }.toSet()
        val body = NumbersScreen.body(numbers)
        val text =
            (
                listOf(body.eyebrow, body.title, body.lead) +
                    body.sections.flatMap { s -> listOf(s.title) + s.points.flatMap { listOf(it.name, it.text) } }
            ).joinToString("\n")
                .replace(numbers.measured, "")
                .replace(numbers.commit.take(7), "")
        val written = NUMBER.findAll(text).map { it.value.replace(",", "") }.toList()
        assertTrue(written.size >= 20, "the page writes its numbers: $written")
        val unmeasured = written.filter { it !in renderings }
        assertEquals(emptyList(), unmeasured, "numbers on the page that the data file does not hold:\n$text")
    }

    /** The control for the test above: a number typed into the copy is found. */
    @Test
    fun `a number the data does not hold is found`() {
        val data = Json.parseToJsonElement(resource(StackNumbers.RESOURCE))
        val renderings = leaves(data).flatMap { renderings(it) }.toSet()
        val typed = NUMBER.findAll("about 123456.7 ms").map { it.value }.toList()
        assertEquals(listOf("123456.7"), typed)
        assertTrue(typed.none { it in renderings })
    }

    /** The goldens draw the page from a wire body; this holds it to what the server sends. */
    @Test
    fun `the numbers page's body is what the server sends`() {
        val drawn =
            Json
                .parseToJsonElement(File(bodies, "numbers_content.json").readText())
                .jsonObject
                .getValue("children")
                .jsonArray
                .single { it.jsonObject["type"] == JsonPrimitive("haul_about") }
        assertEquals(Json.parseToJsonElement(haulWireJson.encodeKompotComponent(NumbersScreen.body())), drawn)
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader.getResource(name)) { "no $name" }.readText()

    private fun leaves(element: JsonElement): List<Double> =
        when (element) {
            is JsonObject -> element.values.flatMap { leaves(it) }
            is JsonArray -> element.flatMap { leaves(it) }
            is JsonPrimitive -> listOfNotNull(element.takeUnless { it.isString }?.doubleOrNull)
        }

    private fun renderings(value: Double): List<String> =
        listOf(
            value.roundToLong().toString(),
            String.format(Locale.ROOT, "%.1f", value),
            String.format(Locale.ROOT, "%.1f", value / 1000),
            (value * 100).roundToLong().toString(),
        )

    private companion object {
        /** A number in prose: not the digits of a name such as «p99» or «B-173», commas as thousands. */
        val NUMBER = Regex("""(?<![\p{L}\d.\-/])\d[\d,]*(\.\d+)?""")
    }
}
