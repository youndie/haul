package io.github.youndie.haul.shell

import androidx.compose.ui.graphics.toArgb
import io.github.youndie.haul.StorefrontPage
import io.github.youndie.haul.theme.HaulColorScheme
import io.github.youndie.haul.theme.THEME_ATTRIBUTE
import io.github.youndie.haul.theme.THEME_KEY
import io.github.youndie.haul.theme.ThemeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    /**
     * Anything else the page waited for — a stylesheet, a font face, an image — would be a round trip before the
     * frame. The fonts' preloads (B-150) and the tab's icon (B-152) are not waited for: the preloads only start the
     * files' transfer, and the browser paints without the icon.
     */
    @Test
    fun `the page asks for nothing but the bundle's loader, the fonts' preloads and its icon`() {
        val fetched = Regex("""\b(?:src|href)\s*=\s*"([^"]*)"""").findAll(page).map { it.groupValues[1] }.toList()
        val fonts = fetched.filter { it.startsWith(FONT_PATH) }
        assertEquals(listOf("/", ICON) + fonts + "composeApp.js", fetched)
        assertEquals(
            FONT_FILES.size + 1,
            Regex("<link").findAll(page).count(),
            "a <link> other than the fonts' preloads and the icon",
        )
        listOf("url(", "@import", "<img").forEach { assertTrue(it !in page, "index.html has $it") }
    }

    /**
     * B-152: a page that names no icon makes the browser ask for `/favicon.ico` on every first load, which nothing
     * serves — a 404 and a console error each time. The page names its own, a file of the bundle (so the store's
     * origin serves it), an SVG that fetches nothing further: no link off the origin, no script.
     */
    @Test
    fun `the page names its icon, a file of the bundle that fetches nothing`() {
        assertTrue("""<link rel="icon" href="$ICON" type="image/svg+xml">""" in page, "index.html names no icon")
        val icon = File("src/wasmJsMain/resources/$ICON")
        assertTrue(icon.isFile, "$ICON is not in the bundle's resources")
        val svg = icon.readText()
        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\""), "$ICON is not an SVG")
        listOf("href", "<script", "url(", "@import", "<image").forEach { assertTrue(it !in svg, "$ICON has $it") }
    }

    /**
     * B-150: the store's first frame is drawn in its faces, which the entry point reads before it mounts. Preloaded
     * as `fetch` with CORS — what Compose's resources send — so the read takes the preloaded answer; preloaded as a
     * `font`, or without `crossorigin`, the browser would fetch every file twice. Every bundled face, none else.
     */
    @Test
    fun `the page preloads every bundled font the way the entry point reads it`() {
        val preloads =
            Regex("""<link rel="preload" href="$FONT_PATH([^"]+)" as="fetch" crossorigin>""")
                .findAll(page)
                .map { it.groupValues[1] }
                .toList()
        assertEquals(FONT_FILES, preloads.sorted())
        assertTrue(page.indexOf("<link") < page.indexOf("id=\"$STATIC_FRAME_ID\""), "the preloads come after the frame")
    }

    /**
     * B-90: a cold `/checkout` drew the storefront's header shape, then checkout's own. The page marks the
     * address — before the frame is parsed, so its first paint already has the mark — and the mark draws the
     * checkout's shape in place of the storefront's rows. The address is checkout's and nothing else's.
     */
    @Test
    fun `a cold checkout draws the checkout's header shape`() {
        val mark =
            Regex(
                """<script>if \(location\.pathname === "([^"]+)"\) """ +
                    """document\.documentElement\.className = "checkout";</script>""",
            )
        val found = mark.find(page)
        assertNotNull(found, "no script marks the checkout's address")
        val path = found.groupValues[1]
        assertEquals(StorefrontPage.Checkout, StorefrontPage.of(path))
        assertTrue(found.range.first < page.indexOf("id=\"$STATIC_FRAME_ID\""), "the mark comes after the frame")
        assertTrue("<div class=\"co\">" in page, "no checkout header in the frame")
        listOf(
            ".checkout #haul-frame .strip, .checkout #haul-frame .row, .checkout #haul-frame .words { display: none; }",
            ".checkout #haul-frame .co { display: flex;",
            "#haul-frame .co { display: none; }",
        ).forEach { assertTrue(it in page, "index.html lacks «$it»") }
    }

    /**
     * B-180: a dark system saw a white page while the bundle loaded. The frame is drawn in the schemes' roles — the
     * light one, the dark one where the browser asks for it and nothing was chosen, the dark one where the shopper
     * chose it — and each value is the scheme's own, so the frame and the first picture Compose draws are one colour.
     */
    @Test
    fun `the frame is drawn in the light and the dark scheme's roles`() {
        val blocks = Regex("""(:root[^{]*)\{([^}]*)\}""").findAll(page).map { it.groupValues[1].trim() to it.groupValues[2] }
        val light = blocks.single { it.first == ":root" }.second
        val darkWhenAsked = blocks.single { it.first == """:root:not([$THEME_ATTRIBUTE="light"])""" }.second
        val darkWhenChosen = blocks.single { it.first == """:root[$THEME_ATTRIBUTE="dark"]""" }.second
        assertEquals(roles(HaulColorScheme.Light), values(light))
        assertEquals(roles(HaulColorScheme.Dark), values(darkWhenAsked))
        assertEquals(roles(HaulColorScheme.Dark), values(darkWhenChosen))
        assertTrue("@media (prefers-color-scheme: dark)" in page, "the dark roles do not follow the browser's scheme")
        val rest = page.substringAfter(":root[$THEME_ATTRIBUTE=\"dark\"]").substringAfter("}")
        val written = Regex("""#[0-9A-Fa-f]{3,8}\b""").findAll(rest).toList()
        assertEquals(emptyList(), written.map { it.value }, "a colour the frame writes outside the roles")
    }

    /**
     * B-180: the shopper's choice is marked on the page's root before the frame is parsed — read from the key the store
     * keeps it under, as one of the two schemes the styles name — so a reload in the chosen scheme paints in it.
     */
    @Test
    fun `the page marks the kept choice before the frame`() {
        val mark =
            """<script>try { var t = localStorage.getItem("$THEME_KEY"); if (t === "${ThemeMode.Light.key}" || """ +
                """t === "${ThemeMode.Dark.key}") document.documentElement.setAttribute("$THEME_ATTRIBUTE", t); } """ +
                """catch (e) {}</script>"""
        val at = page.indexOf(mark)
        assertTrue(at >= 0, "index.html does not mark the kept theme")
        assertTrue(at < page.indexOf("id=\"$STATIC_FRAME_ID\""), "the mark comes after the frame")
    }

    /** The roles a frame's block names, each as the scheme has it. */
    private fun roles(scheme: HaulColorScheme): Map<String, String> =
        mapOf(
            "background" to scheme.background,
            "lowest" to scheme.surfaceContainerLowest,
            "high" to scheme.surfaceContainerHigh,
            "ink" to scheme.onSurface,
            "rule" to scheme.outlineVariant,
            "control" to scheme.outlineControl,
            "primary" to scheme.primary,
            "inverse" to scheme.inverseSurface,
            "band" to scheme.band,
        ).mapValues { (_, colour) -> "#%06X".format(colour.toArgb() and 0xFFFFFF) }

    private fun values(block: String): Map<String, String> =
        Regex("""--([a-z]+):\s*(#[0-9A-Fa-f]{6})""").findAll(block).associate { it.groupValues[1] to it.groupValues[2].uppercase() }

    /**
     * Within a first TCP window (ten segments, about 14 KB) even uncompressed: the frame arrives in the page's
     * first round trip, whatever compresses it on the way.
     */
    @Test
    fun `the page fits the first round trip`() {
        val size = page.toByteArray().size
        assertTrue(size < 14 * 1024, "index.html is $size bytes")
    }

    private companion object {
        /** Where the bundle serves Compose's resources (`packageOfResClass` in the module's build). */
        const val FONT_PATH = "composeResources/io.github.youndie.haul.resources/font/"

        /** The tab's icon, beside the page in the bundle. */
        const val ICON = "favicon.svg"

        /** The faces the module bundles. */
        val FONT_FILES: List<String> = File("src/commonMain/composeResources/font").list()!!.sorted()
    }
}
