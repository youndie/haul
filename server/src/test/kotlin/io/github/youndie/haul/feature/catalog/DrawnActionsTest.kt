package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.putLine
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.AppliedFilters
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.CampaignRow
import io.github.youndie.haul.ui.CategoryGrid
import io.github.youndie.haul.ui.Facet
import io.github.youndie.haul.ui.FacetPanel
import io.github.youndie.haul.ui.FacetRange
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.HaulPagination
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.decodeKompotAction
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
 * B-37 and B-49: the controls the screens drew without anything to follow now carry an action in the
 * tree — a `navigate` to an address the client maps to a screen route, a `load` of the parts of one
 * (B-63), or a cart command — and following it answers the page the control promises. A recent search's row is `RecentSearchesRoutesTest`'s and
 * the «HAUL PLUS» pill of a customer `MembershipRoutesTest`'s, both against a running shildik.
 */
class DrawnActionsTest {
    private val seed = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)

    @Test
    fun `the header links every top-level category and its deals cart and orders`() =
        haulTest {
            listOf("/ui/home", "/ui/c/electronics/audio/headphones", "/ui/deals").forEach { path ->
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
                // A guest's «Orders» is sign-in, as the account shortcut is (B-18).
                assertEquals(NavigateAction("/sign-in"), header.orders, path)
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
            assertEquals(LoadAction("/ui/parts/c/electronics?rating=4.0&sort=price-asc&page=2"), pagination.moreAction)
            assertEquals(pagination.pages.filter { it != "1" && it != "…" }, pagination.links.map { it.label })

            val second = follow(pagination.moreAction)
            assertEquals(2, second.only<HaulPagination>().current)
            val firstCards = first.only<ProductGrid>().cards.map { it.productId }
            val secondCards = second.only<ProductGrid>().cards.map { it.productId }
            assertTrue(secondCards.none { it in firstCards }, "page 2 repeats page 1")
            assertEquals(1, second.only<AppliedFilters>().filterCount, "the rating was lost on the way")
            // Back to the first page from the second: no `page` in the address.
            val back = second.only<HaulPagination>().links.single { it.label == "1" }
            assertEquals(LoadAction("/ui/parts/c/electronics?rating=4.0&sort=price-asc"), back.action)

            val last = tree("/ui/c/electronics?rating=4.0&page=${pagination.pages.last()}").only<HaulPagination>()
            assertNull(last.moreAction, "«Show 24 more» on the last page")
        }

    @Test
    fun `the sort offers every order and keeps the filters`() =
        haulTest {
            val applied = tree("/ui/c/electronics/audio/headphones?brand=Sony&page=2").only<AppliedFilters>()
            assertEquals(
                listOf("Popular", "Price: low to high", "Price: high to low", "Rating", "Newest"),
                applied.sorts.map { it.label },
            )
            val cheapest = applied.sorts.single { it.label == "Price: low to high" }
            assertEquals(
                LoadAction("/ui/parts/c/electronics/audio/headphones?brand=Sony&sort=price-asc"),
                cheapest.action,
            )
            val sorted = follow(cheapest.action)
            assertEquals("Price: low to high", sorted.only<AppliedFilters>().sortLabel)
            val prices = sorted.only<ProductGrid>().cards.map(::dollars)
            assertTrue(prices.size > 1, "too few Sony headphones to see an order")
            assertEquals(prices.sorted(), prices)
        }

    @Test
    fun `clear all drops every filter and keeps the sort`() =
        haulTest {
            val applied =
                tree(
                    "/ui/c/electronics/audio/headphones?brand=Sony&colour=Black&sort=rating",
                ).only<AppliedFilters>()
            assertEquals(LoadAction("/ui/parts/c/electronics/audio/headphones?sort=rating"), applied.clearAction)
            val cleared = follow(applied.clearAction).only<AppliedFilters>()
            assertEquals(0, cleared.filterCount)
            assertEquals("Rating", cleared.sortLabel)
        }

    /**
     * B-69: the price facet carried nothing, so a range could only be typed into the address. Its [FacetRange]
     * is the address of the `load` that applies one — the category's one address, every other filter and the
     * sort kept, from the first page — completed with the two bounds; a bound left empty is left out, and the
     * applied range is a chip that removes it.
     */
    @Test
    fun `the price range applies with a load and its chip removes it`() =
        haulTest {
            val page = tree("/ui/c/electronics/audio/headphones?brand=Sony&sort=price-asc&page=2")
            val range =
                assertNotNull(
                    page
                        .only<FacetPanel>()
                        .facets
                        .single { it.key == "price" }
                        .range,
                )
            assertEquals(null to null, range.low to range.high)
            assertEquals(
                "/ui/parts/c/electronics/audio/headphones?brand=Sony&price_min={min}&price_max={max}&sort=price-asc",
                range.template,
            )
            // The track ends past the category's dearest product, whatever the other filters are.
            val dearest =
                dollars(
                    tree("/ui/c/electronics/audio/headphones?sort=price-desc")
                        .only<ProductGrid>()
                        .cards
                        .first(),
                )
            assertTrue(range.top >= dearest, "the track ends at $${range.top}, under a $dearest card")

            val both = range.applying(80, 400)
            assertEquals(
                LoadAction(
                    "/ui/parts/c/electronics/audio/headphones?brand=Sony&price_min=80&price_max=400&sort=price-asc",
                ),
                both,
            )
            val ranged = follow(both)
            val prices = ranged.only<ProductGrid>().cards.map(::dollars)
            assertTrue(prices.isNotEmpty(), "no Sony headphones between $80 and $400 — nothing to see filtered")
            assertTrue(prices.all { it in 80.0..400.99 }, "outside $80 – $400: $prices")
            assertEquals(prices.sorted(), prices, "the sort was lost on the way")
            val applied = ranged.only<AppliedFilters>()
            assertEquals(2, applied.filterCount, "the brand and the range")
            val chip = applied.chips.single { it.label == "$80 – $400" }
            val after =
                assertNotNull(
                    ranged
                        .only<FacetPanel>()
                        .facets
                        .single { it.key == "price" }
                        .range,
                )
            assertEquals(80 to 400, after.low to after.high)

            // One bound: the other is left out of the address, and the chip names the one set.
            assertEquals(
                LoadAction("/ui/parts/c/electronics/audio/headphones?brand=Sony&price_min=150&sort=price-asc"),
                range.applying(150, null),
            )
            assertEquals(listOf("From $150"), follow(range.applying(150, null)).only<AppliedFilters>().priceChips())
            assertEquals(listOf("Up to $90"), follow(range.applying(null, 90)).only<AppliedFilters>().priceChips())

            val removed = follow(chip.action)
            assertEquals(1, removed.only<AppliedFilters>().filterCount, "the chip removed more than the range")
            val open =
                assertNotNull(
                    removed
                        .only<FacetPanel>()
                        .facets
                        .single { it.key == "price" }
                        .range,
                )
            assertEquals(null to null, open.low to open.high)
        }

    private fun AppliedFilters.priceChips(): List<String> = chips.map { it.label }.filter { "$" in it }

    @Test
    fun `search results page with the query kept`() =
        haulTest {
            val pagination = tree("/ui/search?q=everyday").only<HaulPagination>()
            assertTrue(pagination.pages.size > 1, "one page of «everyday» — nothing to page through")
            assertEquals(LoadAction("/ui/parts/search?q=everyday&page=2"), pagination.moreAction)
            assertEquals(2, follow(pagination.moreAction).only<HaulPagination>().current)
        }

    @Test
    fun `a card's plus puts one more of its SKU into the cart`() =
        haulTest {
            val guest = guest()
            val mug = "${SampleCatalog.STONEWARE_MUG}-0"
            val add = assertNotNull(mugCard(guest).add, "a mug in stock has no «+»")
            // Answered with the header and the card again (B-63, `LineAnswersTest`).
            assertEquals(CartPaths.line(mug) + "?answer=card", add.url)
            assertEquals(LineChange(quantity = 1), add.change)

            putLine(guest, mug, add.change).assertRefresh()
            val after = tree("/ui/c/home-kitchen/kitchen/mugs", guest)
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
                    .mapNotNull { it.add?.url?.substringBefore('?') }
            assertTrue(adds.isNotEmpty(), "no deal offers «+»")
            assertTrue(dealLines.containsAll(adds), "a deal's «+» adds another SKU: $adds")
        }

    /**
     * B-59: home's banners led nowhere. Each now opens the deals page — neither banner's campaign has a SKU
     * of its own to filter the page by — and following it draws that page.
     */
    @Test
    fun `each of home's banners opens the deals page`() =
        haulTest {
            val banners = tree("/ui/home").only<CampaignRow>().banners
            assertEquals(listOf("banner-tech-week", "banner-free-delivery-weekend"), banners.map { it.id })
            assertTrue(seed.skus.none { it.campaignSlug in banners.map { b -> b.id.removePrefix("banner-") } })
            banners.forEach { banner ->
                assertEquals(NavigateAction("/deals"), banner.action, banner.id)
                assertEquals("Deals", follow(banner.action).only<PageTitle>().title, banner.id)
            }
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
     * B-49: home's «All N categories» carried nothing, and no page listed every category — the home page
     * shows eight of them and the header's row ten. It now opens the catalog's root, `/c`, whose tiles are
     * every top-level category in the header's order, each to its own page.
     */
    @Test
    fun `all categories on the home page opens the catalog root`() =
        haulTest {
            val topLevel = seed.categories.filter { it.parentSlug == null }.sortedBy { it.position }
            val link = tree("/ui/home").all().filterIsInstance<SectionHeader>().single { it.id == "categories-title" }
            assertEquals("All ${topLevel.size} categories", link.linkLabel)
            assertEquals(NavigateAction("/c"), link.action)

            val root = follow(link.action)
            assertEquals("Catalog", root.only<PageTitle>().title)
            assertEquals("${topLevel.size} categories", root.only<PageTitle>().count)
            val tiles = root.only<CategoryGrid>().tiles
            assertEquals(topLevel.map { it.name }, tiles.map { it.name }, "the root does not list every category")
            assertEquals(topLevel.map { NavigateAction("/c/${it.slug}") }, tiles.map { it.action })
            assertEquals("Books", follow(tiles.single { it.name == "Books" }.action).only<PageTitle>().title)
        }

    /**
     * B-49: the brand facet's «Show N more» carried nothing. It opens the same page — the filters, the sort
     * and the page kept — with every brand listed and no «Show more» left; a brand ticked there keeps the
     * facet expanded, so the list does not fold back under the shopper's finger.
     */
    @Test
    fun `show more lists every brand with the filters the sort and the page kept`() =
        haulTest {
            val shown = brands(tree("/ui/c/electronics?rating=4.0&sort=price-asc&page=2"))
            val more = assertNotNull(shown.moreLabel, "electronics shows every brand — nothing to expand")
            val hidden = more.removePrefix("Show ").removeSuffix(" more").toInt()
            assertEquals(
                LoadAction("/ui/parts/c/electronics?rating=4.0&expand=brand&sort=price-asc&page=2"),
                shown.moreAction,
            )

            val expandedPage = follow(shown.moreAction)
            val expanded = brands(expandedPage)
            assertEquals(shown.options.size + hidden, expanded.options.size, "the facet did not list every brand")
            assertEquals(shown.options.map { it.label }, expanded.options.take(shown.options.size).map { it.label })
            assertNull(expanded.moreLabel)
            assertNull(expanded.moreAction)
            assertEquals(1, expandedPage.only<AppliedFilters>().filterCount, "the rating was lost on the way")
            assertEquals("Price: low to high", expandedPage.only<AppliedFilters>().sortLabel)
            assertEquals(2, expandedPage.only<HaulPagination>().current)

            val ticked = follow(expanded.options.last().action)
            assertEquals(expanded.options.size, brands(ticked).options.size, "ticking a brand folded the facet")
        }

    @Test
    fun `a facet other than the brand's does not expand`() =
        haulTest {
            get("/ui/c/electronics?expand=colour").assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
        }

    /** A guest's «HAUL PLUS» pill asks for a sign-in, as the Plus block's «Try 30 days free» does (B-49). */
    @Test
    fun `the plus pill asks a guest to sign in`() =
        haulTest {
            listOf("/ui/home", "/ui/c", "/ui/c/electronics/audio/headphones", "/ui/deals").forEach { path ->
                assertEquals(NavigateAction("/sign-in"), tree(path).only<HaulHeader>().plus, path)
            }
        }

    private fun brands(page: KompotComponent): Facet = page.only<FacetPanel>().facets.single { it.key == "brand" }

    /**
     * How far a product's shown SKU — the cheapest in stock, else the cheapest, as the cards price it —
     * is under its old price, as a fraction of the old; 0 when it is not on sale. A deal of the canvas's day
     * below a SKU's price is that SKU's price, over the price it beats (B-57): the store prices it so on
     * every card, so a generated deal outside the sale is on sale too.
     */
    private fun markdown(productId: String): Double {
        val skus =
            seed.skus.filter { it.productId == productId }.map { sku ->
                val deal = seed.deals.filter { it.skuId == sku.id }.minOfOrNull { it.priceCents }
                if (deal == null ||
                    deal >= sku.priceCents
                ) {
                    sku
                } else {
                    sku.copy(priceCents = deal, oldPriceCents = sku.priceCents)
                }
            }
        val shown = skus.filter { it.stock > 0 }.minByOrNull { it.priceCents } ?: skus.minBy { it.priceCents }
        val old = shown.oldPriceCents ?: return 0.0
        return if (old > shown.priceCents) (old - shown.priceCents).toDouble() / old else 0.0
    }

    private suspend fun HttpClient.mugCard(guest: String): ProductCard =
        tree("/ui/c/home-kitchen/kitchen/mugs", guest).only<ProductGrid>().cards.single {
            it.productId ==
                SampleCatalog.STONEWARE_MUG
        }

    private suspend fun HttpClient.tree(
        path: String,
        guest: String,
    ): KompotComponent {
        val response = get(path) { header(GUEST_HEADER, guest) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    /**
     * Where the client goes for a `navigate`: the same address under `/ui`, `/` being `/ui/home`. A `load`
     * (B-63) is answered with the address its parts make, and the page there is what the shopper then sees.
     */
    private suspend fun HttpClient.follow(action: KompotAction?): KompotComponent {
        val deeplink =
            when (action) {
                is NavigateAction -> {
                    action.deeplink
                }

                is LoadAction -> {
                    assertNotNull(
                        answer(action.url) as? UpdateAction,
                        "${action.url} is no update",
                    ).deeplink
                }

                else -> {
                    null
                }
            }
        assertNotNull(deeplink, "$action leads nowhere")
        return tree(if (deeplink == "/") "/ui/home" else "/ui$deeplink")
    }

    private suspend fun HttpClient.answer(url: String): KompotAction {
        val response = get(url)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotAction(response.bodyAsText())
    }

    private fun dollars(card: ProductCard): Double =
        card.price
            .removePrefix("$")
            .replace(",", "")
            .toDouble()
}
