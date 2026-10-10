package io.github.youndie.haul.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The page the browser shows before the bundle runs (B-80): `index.html` draws the header's shape itself, in
 * the first round trip, and the entry point finds it by [STATIC_FRAME_ID] to take it away. Whether it is
 * taken away in the frame Compose first draws is measured in a browser (`scripts/measure-first-load.py`).
 */
class StaticFrameTest {
    private val page = File("src/wasmJsMain/resources/index.html").readText()

    @Test
    fun `the page carries the frame the entry point removes`() {
        assertTrue("id=\"$STATIC_FRAME_ID\"" in page, "no element with id $STATIC_FRAME_ID in index.html")
    }

    /** Parsed before the loader's script, the frame is painted while the bundle is still on its way. */
    @Test
    fun `the frame comes before the bundle's script`() {
        val frame = page.indexOf("id=\"$STATIC_FRAME_ID\"")
        val script = page.indexOf("<script src=\"composeApp.js\">")
        assertTrue(frame in 0 until script, "the frame at $frame, the script at $script")
    }

    /** Anything else the page asked for — a stylesheet, a font, an image — would be a round trip before the frame. */
    @Test
    fun `the page asks for nothing but the bundle's loader`() {
        val fetched = Regex("""\b(?:src|href)\s*=\s*"([^"]*)"""").findAll(page).map { it.groupValues[1] }.toList()
        assertEquals(listOf("/", "composeApp.js"), fetched)
        listOf("<link", "url(", "@import", "<img").forEach { assertTrue(it !in page, "index.html has $it") }
    }

    /**
     * Within a first TCP window (ten segments, about 14 KB) even uncompressed: the frame arrives in the page's
     * first round trip, whatever compresses it on the way.
     */
    @Test
    fun `the page fits the first round trip`() {
        val size = page.toByteArray().size
        assertTrue(size < 14 * 1024, "index.html is $size bytes")
    }
}
