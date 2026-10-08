package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.putLine
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulPagination
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-37: the controls the screens drew without anything to follow now carry an action in the tree — a
 * `navigate` to an address the client maps to a screen route, or a cart command — and following it
 * answers the page the control promises.
 */
class DrawnActionsTest {
    private val seed = CatalogSeed.generate()

    @Test
    fun `the header links every top-level category and its deals and cart`() =
        haulTest {
            listOf("/ui/home", "/ui/c/headphones", "/ui/deals").forEach { path ->
                val header = tree(path).only<HaulHeader>()
                val topLevel = seed.categories.filter { it.parentSlug == null }.sortedBy { it.position }
                assertEquals(topLevel.map { it.name }, header.catalog.map { it.label }, "$path: «Catalog»")
                assertEquals(
                    topLevel.map { NavigateAction("/c/${it.slug}") },
                    header.catalog.map { it.action },
                    "$path: «Catalog»'s links",
                )
                header.categories.forEach { name ->
                    assertNotNull(header.catalog.single { it.label == name }.action, "$path: «$name» in the row")
                }
                assertEquals(NavigateAction("/deals"), header.deals, path)
                assertEquals(NavigateAction("/cart"), header.cart, path)
            }
            // Followed: a word of the row is that category's page.
            val electronics = tree("/ui/home").only<HaulHeader>().catalog.first { it.label == "Electronics" }
            assertEquals("Electronics", follow(electronics.action).only<PageTitle>().title)
        }

    @Test
    fun `pages and show more go to the next page with the filters kept`() =
        haulTest {
            val first = tree("/ui/c/electronics?rating=4.0&sort=price-asc")
            val pagination = first.only<HaulPagination>()
            assertTrue(pagination.pages.size > 1, "one page of electronics rated 4 and up — nothing to page through")
            assertEquals(NavigateAction("/c/electronics?rating=4.0&sort=price-asc&page=2"), pagination.moreAction)
            assertEquals(pagination.pages.filter { it != "1" && it != "…" }, pagination.links.map { it.label })

            val second = follow(pagination.moreAction)
            assertEquals(2, second.only<HaulPagination>().current)
            val firstCards = first.only<ProductGrid>().cards.map { it.productId }
            val secondCards = second.only<ProductGrid>().cards.map { it.productId }
            assertTrue(secondCards.none { it in firstCards }, "page 2 repeats page 1")
            assertEquals(1, second.only<AppliedFilters>().filterCount, "the rating was lost on the way")
            // Back to the first page from the second: no `page` in the address.
            val back = second.only<HaulPagination>().links.single { it.label == "1" }
            assertEquals(NavigateAction("/c/electronics?rating=4.0&sort=price-asc"), back.action)

            val last = tree("/ui/c/electronics?rating=4.0&page=${pagination.pages.last()}").only<HaulPagination>()
            assertNull(last.moreAction, "«Show 24 more» on the last page")
        }

    @Test
    fun `the sort offers every order and keeps the filters`() =
        haulTest {
            val applied = tree("/ui/c/headphones?brand=Sony&page=2").only<AppliedFilters>()
            assertEquals(
                listOf("Popular", "Price: low to high", "Price: high to low", "Rating", "Newest"),
                applied.sorts.map { it.label },
            )
            val cheapest = applied.sorts.single { it.label == "Price: low to high" }
            assertEquals(NavigateAction("/c/headphones?brand=Sony&sort=price-asc"), cheapest.action)
            val sorted = follow(cheapest.action)
            assertEquals("Price: low to high", sorted.only<AppliedFilters>().sortLabel)
            val prices = sorted.only<ProductGrid>().cards.map(::dollars)
            assertTrue(prices.size > 1, "too few Sony headphones to see an order")
            assertEquals(prices.sorted(), prices)
        }

    @Test
    fun `clear all drops every filter and keeps the sort`() =
        haulTest {
            val applied = tree("/ui/c/headphones?brand=Sony&colour=Black&sort=rating").only<AppliedFilters>()
            assertEquals(NavigateAction("/c/headphones?sort=rating"), applied.clearAction)
            val cleared = follow(applied.clearAction).only<AppliedFilters>()
            assertEquals(0, cleared.filterCount)
            assertEquals("Rating", cleared.sortLabel)
        }

    @Test
    fun `search results page with the query kept`() =
        haulTest {
            val pagination = tree("/ui/search?q=everyday").only<HaulPagination>()
            assertTrue(pagination.pages.size > 1, "one page of «everyday» — nothing to page through")
            assertEquals(NavigateAction("/search?q=everyday&page=2"), pagination.moreAction)
            assertEquals(2, follow(pagination.moreAction).only<HaulPagination>().current)
        }

    @Test
    fun `a card's plus puts one more of its SKU into the cart`() =
        haulTest {
            val guest = guest()
            val mug = "${SampleCatalog.STONEWARE_MUG}-0"
            val add = assertNotNull(mugCard(guest).add, "a mug in stock has no «+»")
            assertEquals(CartPaths.line(mug), add.url)
            assertEquals(LineChange(quantity = 1), add.change)

            putLine(guest, mug, add.change).assertRefresh()
            val after = tree("/ui/c/mugs", guest)
            assertEquals(1, after.only<HaulHeader>().cartCount, "«+» did not reach the header's count")
            val next = assertNotNull(mugCard(guest).add)
            assertEquals(LineChange(quantity = 2), next.change, "a second «+» would not add one more")

            // Ten is as many as a line holds: «+» is gone, not a request the server refuses.
            putLine(guest, mug, LineChange(quantity = 10)).assertRefresh()
            assertNull(mugCard(guest).add)
        }

    @Test
    fun `a card out of stock has no plus`() =
        haulTest {
            val gone =
                seed.products.first { product ->
                    seed.skus.filter { it.productId == product.id }.all { it.stock == 0 } &&
                        seed.products.count { it.categorySlug == product.categorySlug } <= 24
                }
            val card =
                tree("/ui/c/${gone.categorySlug}")
                    .only<ProductGrid>()
                    .cards
                    .single { it.productId == gone.id }
            assertNull(card.add, "${gone.id} is out of stock and still offers «+»")
        }

    @Test
    fun `the home page's deal links go to the deals page`() =
        haulTest {
            val home = tree("/ui/home")
            val viewAll = home.all().filterIsInstance<SectionHeader>().single { it.id == "deals-title" }
            assertEquals("View all deals", viewAll.linkLabel)
            assertEquals(NavigateAction("/deals"), viewAll.action)
            assertEquals(NavigateAction("/deals"), home.only<CampaignHero>().action)
            // A deal's «+» adds the deal's SKU, the one whose price the card shows.
            val dealLines = seed.deals.map { CartPaths.line(it.skuId) }.toSet()
            val adds =
                home
                    .all()
                    .filterIsInstance<ProductGrid>()
                    .single { it.id == "deals" }
                    .cards
                    .mapNotNull { it.add?.url }
            assertTrue(adds.isNotEmpty(), "no deal offers «+»")
            assertTrue(dealLines.containsAll(adds), "a deal's «+» adds another SKU: $adds")
        }

    @Test
    fun `the deals page shows today's deals and everything on sale deepest first`() =
        haulTest {
            val page = follow(NavigateAction("/deals"))
            val grids = page.all().filterIsInstance<ProductGrid>()
            assertEquals(6, grids.single { it.id == "deals" }.cards.size)
            val countdown = page.all().filterIsInstance<SectionHeader>().single { it.id == "deals-title" }
            assertEquals("2025-10-08T00:00-04:00", countdown.countdownEndsAt)
            val onSale = grids.single { it.id == "grid" }.cards
            assertEquals(24, onSale.size)
            assertTrue(onSale.all { it.oldPrice != null }, "a product not on sale is on the deals page")
            val markdowns = onSale.map { markdown(it.productId) }
            assertEquals(markdowns.sortedDescending(), markdowns, "the deepest discount is not first")
            val discounted = seed.products.count { markdown(it.id) > 0.0 }
            assertEquals("%,d items on sale".format(discounted), page.only<PageTitle>().count)

            val pagination = page.only<HaulPagination>()
            assertEquals(NavigateAction("/deals?page=2"), pagination.moreAction)
            val second = follow(pagination.moreAction)
            assertTrue(second.all().filterIsInstance<ProductGrid>().none { it.id == "deals" }, "today's deals again")
            assertNotEquals(
                onSale.first().productId,
                second
                    .only<ProductGrid>()
                    .cards
                    .first()
                    .productId,
            )
        }

    @Test
    fun `page zero of the deals is refused`() =
        haulTest {
            assertEquals(HttpStatusCode.BadRequest, get("/ui/deals?page=0").status)
        }

    /**
     * How far a product's shown SKU — the cheapest in stock, else the cheapest, as the cards price it —
     * is under its old price, as a fraction of the old; 0 when it is not on sale.
     */
    private fun markdown(productId: String): Double {
        val skus = seed.skus.filter { it.productId == productId }
        val shown = skus.filter { it.stock > 0 }.minByOrNull { it.priceCents } ?: skus.minBy { it.priceCents }
        val old = shown.oldPriceCents ?: return 0.0
        return if (old > shown.priceCents) (old - shown.priceCents).toDouble() / old else 0.0
    }

    private suspend fun HttpClient.mugCard(guest: String): ProductCard =
        tree("/ui/c/mugs", guest).only<ProductGrid>().cards.single { it.productId == SampleCatalog.STONEWARE_MUG }

    private suspend fun HttpClient.tree(
        path: String,
        guest: String,
    ): KompotComponent {
        val response = get(path) { header(GUEST_HEADER, guest) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    /** Where the client goes for a `navigate`: the same address under `/ui`, `/` being `/ui/home`. */
    private suspend fun HttpClient.follow(action: KompotAction?): KompotComponent {
        val deeplink = assertNotNull(action as? NavigateAction, "$action is not a navigate").deeplink
        return tree(if (deeplink == "/") "/ui/home" else "/ui$deeplink")
    }

    private fun dollars(card: ProductCard): Double =
        card.price
            .removePrefix("$")
            .replace(",", "")
            .toDouble()
}
