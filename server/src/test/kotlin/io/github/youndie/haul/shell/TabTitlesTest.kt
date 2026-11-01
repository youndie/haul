package io.github.youndie.haul.shell

import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.kompot.decodeKompotComponent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * B-161: every page names the browser tab on its header — the server's copy, which the client puts in
 * `document.title`. The account's pages, the order's and checkout's are held by their fixtures (`AccountFixturesTest`,
 * `OrderFixturesTest`, `SavedFixturesTest`, `CheckoutFixturesTest`), whose bodies carry the header the server writes.
 */
class TabTitlesTest {
    @Test
    fun `every page a guest opens names its tab`() =
        haulTest {
            val guest = guest()
            val titles =
                PAGES.keys.associateWith { path ->
                    val body = get(path) { header(GUEST_HEADER, guest) }.bodyAsText()
                    haulWireJson.decodeKompotComponent(body).only<HaulHeader>().tabTitle
                }
            assertEquals(PAGES, titles)
        }

    /** The header alone tops no page, so it names none (B-105): the client never reads a title from it. */
    @Test
    fun `the header alone names no page`() =
        haulTest {
            assertNull((tree(Frame.HEADER) as HaulHeader).tabTitle)
        }

    private companion object {
        val PAGES =
            linkedMapOf(
                "/ui/home" to "Haul — a demo store built end to end in Kotlin",
                "/ui/c" to "Catalog · Haul",
                "/ui/c/electronics/audio/headphones" to "Headphones · Haul",
                "/ui/p/${SampleCatalog.SONORA_HEADPHONES}" to
                    "Sonora SH-718N Wireless Noise Cancelling Headphones · Haul",
                "/ui/search?q=running%20shoes" to "Search: running shoes · Haul",
                "/ui/search?q=zzqxv" to "Search: zzqxv · Haul",
                "/ui/deals" to "Deals · Haul",
                "/ui/about" to "About this demo · Haul",
                "/ui/numbers" to "The stack in numbers · Haul",
                CartPaths.SCREEN to "Cart · Haul",
            )
    }
}
