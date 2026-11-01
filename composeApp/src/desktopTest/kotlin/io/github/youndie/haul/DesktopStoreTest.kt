package io.github.youndie.haul

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.feature.identity.MemorySessionStore
import io.github.youndie.haul.shell.BackStackHistory
import io.github.youndie.haul.shell.DEMO_NOTE_TREE
import io.github.youndie.haul.shell.HEADER_TREE
import io.github.youndie.haul.theme.HaulColorScheme
import io.github.youndie.haul.theme.MemoryThemeMemory
import io.github.youndie.haul.theme.ThemeChoice
import io.github.youndie.haul.theme.ThemeMode
import io.github.youndie.haul.ui.themeTag
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop app's wiring (B-176) against a server over a mock engine: the same [App] and renderers draw the home
 * page from a home tree on the wire (`resources/bodies/home_guest.json`, the goldens' guest home), and every request,
 * the guest's included, goes to the server the app was pointed at — the store's bare paths too.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopStoreTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val trees = CopyOnWriteArrayList<Pair<String, String?>>()

    /** The stand as far as the home page needs it: a guest, the home tree, the header; anything else is not there. */
    private val server =
        MockEngine { request ->
            val url = request.url
            requests += "${request.method.value} ${url.protocol.name}://${url.host}:${url.port}${url.encodedPath}"
            if (url.host != HOST || url.port != PORT) return@MockEngine respond("", HttpStatusCode.NotFound)
            // The trees go through the shopper's identity; the photos, which need none, do not (B-30).
            if (url.encodedPath.startsWith("/ui/")) trees += url.encodedPath to request.headers[GUEST_HEADER]
            when {
                request.method == HttpMethod.Post && url.encodedPath == "/api/v1/guests" -> {
                    respond("""{"id":"$GUEST"}""", HttpStatusCode.Created, JSON)
                }

                url.encodedPath == "/ui/home" -> {
                    respond(read("home_guest.json"), HttpStatusCode.OK, JSON)
                }

                url.encodedPath == HEADER_TREE -> {
                    respond(read("haul_header_guest.json"), HttpStatusCode.OK, JSON)
                }

                url.encodedPath == DEMO_NOTE_TREE -> {
                    respond(read("demo_note.json"), HttpStatusCode.OK, JSON)
                }

                else -> {
                    respond("""{"code":"not_found","message":"no"}""", HttpStatusCode.NotFound, JSON)
                }
            }
        }

    @Test
    fun `the desktop app draws the home tree its server sends, as the guest the server issued`() =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            val sessions = MemorySessionStore()
            setContent { DesktopStore(storeClient(ORIGIN, server), ORIGIN, BackStackHistory(), sessions) }

            waitUntil(timeoutMillis = 10_000) { exists(HERO_ACTION) }

            assertTrue(requests.isNotEmpty())
            assertTrue(requests.all { "://$HOST:$PORT/" in it }, "every request goes to the server named: $requests")
            assertEquals(1, requests.count { it.endsWith("/api/v1/guests") }, "one guest, asked once: $requests")
            assertTrue("GET $ORIGIN/ui/home" in requests, "the home page is the home screen's tree: $requests")
            assertEquals(GUEST, sessions.load().guestId)
            assertTrue(
                trees.isNotEmpty() && trees.all { it.second == GUEST },
                "each tree is asked for as the guest: $trees",
            )
        }

    /**
     * B-180: the page follows the system's scheme until the shopper chooses in the footer's switch; the choice draws the
     * page in it at once and is kept, so the next run starts in it.
     */
    @Test
    fun `the footer's switch draws the page in the scheme chosen and keeps the choice`() =
        runDesktopComposeUiTest(WIDTH, TALL) {
            val memory = MemoryThemeMemory()
            var systemDark by mutableStateOf(false)
            setContent {
                DesktopStore(
                    storeClient(ORIGIN, server),
                    ORIGIN,
                    BackStackHistory(),
                    MemorySessionStore(),
                    theme = remember { ThemeChoice(memory) },
                    systemDark = systemDark,
                )
            }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(themeTag(ThemeMode.Dark)).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(HaulColorScheme.Light.background, pageGround())

            systemDark = true
            waitForIdle()
            assertEquals(HaulColorScheme.Dark.background, pageGround(), "nothing chosen, the system's scheme")

            onNodeWithTag(themeTag(ThemeMode.Light)).performClick()
            waitForIdle()
            assertEquals(HaulColorScheme.Light.background, pageGround(), "chosen, the choice over the system's")
            assertEquals(ThemeMode.Light, memory.read())
            onNodeWithTag(themeTag(ThemeMode.Light)).assertContentDescriptionEquals("Light, chosen")
            onNodeWithTag(themeTag(ThemeMode.Dark)).assertContentDescriptionEquals("Dark, not chosen")
        }

    /** The page's ground, left of the content in the page's gutter, under the header. */
    private fun ComposeUiTest.pageGround(): Color = onRoot().captureToImage().toPixelMap()[GUTTER_X, GROUND_Y]

    @Test
    fun `the server is the one named, then HAUL_SERVER, then the stand`() {
        assertEquals("http://localhost:8080", serverOrigin(listOf("--server=http://localhost:8080/"), "https://x.test"))
        assertEquals("https://x.test", serverOrigin(listOf("--other"), "https://x.test"))
        assertEquals(STAND, serverOrigin(emptyList(), null))
        assertEquals(STAND, serverOrigin(emptyList(), " "))
    }

    private fun ComposeUiTest.exists(text: String): Boolean =
        onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val HOST = "stand.test"
        const val PORT = 8443
        const val ORIGIN = "https://$HOST:$PORT"
        const val GUEST = "g-desktop"

        /** The hero's button in `home_guest.json`: drawn only once the home tree has arrived and been rendered. */
        const val HERO_ACTION = "Shop the sale"
        const val WIDTH = 1440
        const val HEIGHT = 1200

        /** Tall enough that the home page is laid out down to its footer, where the theme's switch is. */
        const val TALL = 4000
        const val GUTTER_X = 8
        const val GROUND_Y = 1500
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
