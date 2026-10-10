package io.github.youndie.haul.shell

import io.github.youndie.haul.feature.catalog.screen.CatalogScreen
import io.github.youndie.haul.feature.catalog.screen.DealsScreen
import io.github.youndie.haul.feature.search.screen.SearchScreen
import io.github.youndie.haul.testing.after
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.answer
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.json
import io.github.youndie.haul.testing.loads
import io.github.youndie.haul.testing.tree
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * B-63: a press that only filters, sorts or pages a screen is a `load` of the screen's parts
 * (`/ui/parts<address>`, endpoint kind `load`), answered with an `update` of the nodes it changes and the
 * address they make, `push`. Every such press a page carries is followed here, and the page the client draws
 * after the update — the page before it, each named node replaced — has to be the whole page at that address:
 * what back, a reload and a shared link open. An answer that cannot be partial opens the address instead.
 */
class PartsRoutesTest {
    /**
     * Follows every `load` on the page at [address] and holds each answer to the page its address opens;
     * returns how many there were. The parts are only nodes [parts] names: the header only where the search's
     * picker reads the page (B-72).
     */
    private suspend fun HttpClient.everyLoadOf(
        address: String,
        parts: List<String>,
    ): Int {
        val page = tree("/ui$address")
        val loads = page.loads()
        loads.forEach { url ->
            val update = assertNotNull(answer(url) as? UpdateAction, "$url is not answered with an update")
            val deeplink = assertNotNull(update.deeplink, "$url names no address")
            assertEquals(url.removePrefix(Parts.PREFIX), deeplink, url)
            assertEquals(UpdateHistory.PUSH, update.history, url)
            val ids = update.updates.map { it.componentId }
            assertEquals(parts.filter { id -> page.all().any { it.id == id } }, ids, url)
            update.updates.forEach { assertEquals(it.componentId, it.component.id, url) }
            assertEquals(
                tree("/ui$deeplink").json(),
                page.after(update),
                "$url: the page after the update is not $deeplink",
            )
        }
        return loads.size
    }

    @Test
    fun `every filter sort and page of a category answers the parts of the page it opens`() =
        haulTest {
            // The headphones have kinds («Over-ear»), so all three parts travel.
            assertTrue(
                tree("/ui/c/electronics/audio/headphones").all().any { it.id == "kinds" },
                "the headphones have no kinds",
            )
            val headphones = everyLoadOf("/c/electronics/audio/headphones", CatalogScreen.PARTS)
            assertTrue(headphones > 10, "the headphones carry only $headphones loads")
            // Filters, a sort, the brands expanded and a second page, so every kind of press starts from somewhere.
            everyLoadOf("/c/electronics?rating=4.0&expand=brand&sort=price-asc&page=2", CatalogScreen.PARTS)
        }

    @Test
    fun `every category chip and page of a search answers the parts of the page it opens`() =
        haulTest {
            val loads = everyLoadOf("/search?q=everyday", SearchScreen.PARTS)
            assertTrue(loads > 2, "the search carries only $loads loads")
            // B-72: in a top-level category — «All» and a leaf's chip leave the scope, and the picker with it.
            everyLoadOf("/search?q=running%20shoes&category=sports", SearchScreen.PARTS)
        }

    @Test
    fun `the deals load their pages in place except to and from today's deals`() =
        haulTest {
            assertTrue(
                everyLoadOf("/deals?page=2", DealsScreen.PARTS) > 0,
                "the second page of the deals loads nothing",
            )
            // The first page draws today's deals, which an update cannot add or take away.
            val first = tree("/ui/deals")
            assertEquals(emptyList(), first.loads())
            assertEquals(NavigateAction("/deals"), answer("${Parts.PREFIX}/deals"))
        }

    @Test
    fun `a category no longer there opens its address`() =
        haulTest {
            assertEquals(
                NavigateAction("/c/no-such-thing?brand=Sony"),
                answer("${Parts.PREFIX}/c/no-such-thing?brand=Sony"),
            )
            assertEquals(
                NavigateAction("/search?q=everyday&category=gone"),
                answer("${Parts.PREFIX}/search?q=everyday&category=gone"),
            )
        }

    /** What a filter changes, and what it does not: the frame is not in the answer. */
    @Test
    fun `a tick sends the results and not the frame`() =
        haulTest {
            val page = tree("/ui/c/electronics/audio/headphones")
            val tick = page.loads().first { "brand=" in it }
            val update = answer(tick) as UpdateAction
            val sent = update.updates.map { it.componentId }.toSet()
            assertTrue("header" !in sent && "breadcrumbs" !in sent, "the frame came along: $sent")
            assertNotEquals(page.json(), page.after(update), "the tick changed nothing")
        }
}
