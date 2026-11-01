package io.github.youndie.haul.shell

import io.github.youndie.haul.decode
import io.github.youndie.haul.ui.DemoNote
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.Link
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * B-163: what «Under the hood» says about a tree and a request — the outline read from the wire's JSON, nodes an update
 * replaced drawn as replaced, values said short — and the request's numbers as a network panel writes them.
 */
class HoodOutlineTest {
    @Test
    fun `the outline is the tree's nodes by type and id with their fields said short`() {
        val root = HoodOutline.of(page)
        assertEquals("column", root.type)
        assertEquals("page", root.id)
        assertEquals(listOf("haul_header", "text", "haul_demo_note"), root.children.map { it.type })
        assertEquals(listOf("header", "intro", "note"), root.children.map { it.id })
        assertEquals(4, root.count)
        val intro = root.children[1].fields.associate { it.name to it.value }
        assertEquals("\"${LONG.take(VALUE_CHARS - 2)}…", intro["text"], "a long value is not cut")
        assertEquals(VALUE_CHARS, intro.getValue("text").length)
        val note = root.children[2].fields.associate { it.name to it.value }
        assertEquals("[1 item]", note["links"])
        assertEquals("\"Close\"", note["close"])
        val header = root.children[0].fields.associate { it.name to it.value }
        assertEquals("[\"Electronics\", \"Home\"]", header["categories"])
        assertEquals("0", header["cartCount"])
    }

    @Test
    fun `a node an update replaced is drawn as replaced and a later update around it drops the earlier inside`() {
        val screen = HoodScreen(page, "/ui/home")
        screen.replace(listOf(UpdateComponentMessage("intro", TextComponent(id = "intro", text = "Hello"))))
        assertEquals(
            "\"Hello\"",
            screen
                .outline()!!
                .children[1]
                .fields
                .single { it.name == "text" }
                .value,
        )
        // The page replaced whole after it: what was written inside the old page no longer applies.
        screen.replace(listOf(UpdateComponentMessage("page", page)))
        assertTrue(
            LONG.take(10) in
                screen
                    .outline()!!
                    .children[1]
                    .fields
                    .single { it.name == "text" }
                    .value,
        )
        assertNull(HoodScreen(null, "/ui/sign-in").outline())
    }

    @Test
    fun `every page the goldens draw outlines`() {
        listOf("home_content.json", "catalog_content.json", "product_reviews.json", "cart_content.json").forEach {
            val root = HoodOutline.of(decode(it))
            assertTrue(root.count > 5, "$it outlined as ${root.count} nodes")
            assertTrue(root.children.any { child -> child.type == "haul_header" }, it)
        }
    }

    @Test
    fun `the server's time is read off its header and a size and a request are said as a network panel says them`() {
        assertEquals(12.3, serverMillis("app;dur=12.3"))
        assertEquals(7.0, serverMillis("db;dur=1, app;desc=\"tree\";dur=7"))
        assertNull(serverMillis(null))
        assertNull(serverMillis("db;dur=4"))
        assertEquals(6, "héllo".utf8Size())
        assertEquals(4, "😀".utf8Size())
        assertEquals("812 B", 812.said())
        assertEquals("48.2 kB", 48_213.said())
        assertEquals(
            "200 · 48.2 kB · server 12.4 ms · total 37 ms",
            HoodRequest("/ui/home", 200, 48_213, 12.36, 36.6.milliseconds).said(),
        )
        assertEquals("404 · 120 B · total 9 ms", HoodRequest("/ui/p/x", 404, 120, null, 9.milliseconds).said())
    }

    private companion object {
        val LONG = "A sentence much longer than the outline draws a value, so it is cut short at its end."

        val page: KompotComponent =
            ColumnComponent(
                id = "page",
                children =
                    listOf(
                        HaulHeader(
                            id = "header",
                            deliverTo = "Brooklyn",
                            deliveryPromise = "Free",
                            cartCount = 0,
                            searchPlaceholder = "Search",
                            categories = listOf("Electronics", "Home"),
                        ),
                        TextComponent(id = "intro", text = LONG),
                        DemoNote(
                            id = "note",
                            text = "A demo",
                            links = listOf(Link("About", NavigateAction("/about"))),
                            close = "Close",
                        ),
                    ),
            )
    }
}
