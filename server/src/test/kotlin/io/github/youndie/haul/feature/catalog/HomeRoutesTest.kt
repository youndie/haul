package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.PlusBlock
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.standard.NavigateAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The guest home page (`Home_Guest`): what feature-browse promises a shopper who has not signed in. */
class HomeRoutesTest {
    @Test
    fun `a guest sees the campaign the deals and the Plus offer`() =
        haulTest {
            val home = tree("/ui/home")
            assertNull(home.only<HaulHeader>().customerName, "a guest's header names somebody")
            assertEquals("Autumn mega sale · Oct 7 — 14", home.only<CampaignHero>().eyebrow)
            val deals =
                home
                    .all()
                    .filterIsInstance<ProductGrid>()
                    .single { it.id == "deals" }
                    .cards
            assertEquals(6, deals.size)
            assertEquals("$349", deals.first().price)
            assertEquals("−22%", deals.first().badge)
            // The countdown runs to New York's midnight; the client counts, the server sends the instant.
            assertEquals(
                "2025-10-08T00:00-04:00",
                home
                    .all()
                    .filterIsInstance<SectionHeader>()
                    .single {
                        it.id ==
                            "deals-title"
                    }.countdownEndsAt,
            )
            assertFalse(home.only<PlusBlock>().member)
        }

    /**
     * B-105: the header alone is the one every page draws — the client draws it over a page that came with no
     * tree, so its category words lead to their categories, as the tree's do, rather than to the catalog's root.
     * All but the tab's title, which names the page the header tops (B-161), and the header alone tops none.
     */
    @Test
    fun `the header alone is the one the home page draws`() =
        haulTest {
            val header = tree("/ui/header")
            assertEquals(tree("/ui/home").only<HaulHeader>().copy(tabTitle = null), header)
            assertEquals(
                NavigateAction("/c/electronics"),
                (header as HaulHeader).catalog.single { it.label == "Electronics" }.action,
            )
        }
}
