package io.github.youndie.haul.shell

import io.github.youndie.haul.testing.haulTest
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-163: every tree says how long the server took over it, in the W3C `Server-Timing` header the storefront's «Under
 * the hood» panel reads — a screen, its parts, the strip, a refusal of a tree — and nothing else does.
 */
class TreeTimingTest {
    @Test
    fun `every answer under ui names the server's time and nothing else does`() =
        haulTest {
            listOf("/ui/home", "/ui/demo", "/ui/parts/c/electronics?sort=price-asc", "/ui/p/no-such-product").forEach {
                val response = get(it)
                val timing = response.serverTiming()
                assertEquals(1, response.headers.getAll(SERVER_TIMING)?.size, "$it named its time more than once")
                assertTrue(timing != null && TIMING.matches(timing), "$it answered ${response.status} with «$timing»")
            }
            assertEquals(404, get("/ui/p/no-such-product").status.value, "a refused tree still says its time")
            listOf("/healthz", "/api/v1/sign-in").forEach {
                assertNull(get(it).serverTiming(), "$it is no tree and named a server time")
            }
        }

    private fun HttpResponse.serverTiming(): String? = headers[SERVER_TIMING]

    private companion object {
        /** One metric, `app`, in milliseconds to a tenth: what the client's transport parses. */
        val TIMING = Regex("""app;dur=\d+\.\d""")
    }
}
