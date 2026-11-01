package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.cart.domain.Totals
import io.github.youndie.haul.feature.membership.screen.PlusOffer
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.cart
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.tree
import io.github.youndie.haul.ui.CampaignHero
import io.github.youndie.haul.ui.EmptyState
import io.github.youndie.haul.ui.PageTitle
import io.github.youndie.haul.ui.PlusBlock
import io.github.youndie.haul.ui.ProductCard
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.PromoBanner
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.encodeKompotComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The store's promises agree with its rules and with each other (B-172). A shopper reading the stand found
 * «Free delivery over $35» in the header beside a banner «Free delivery on everything» and Haul Plus sold for free
 * delivery; «1.2 million items marked down» over a deals page of 832; «Laptops from $399» opening the deals page of
 * a store that sells no laptops; «Up to −70%» where nothing was more than −49 %. Each was copy nobody had read
 * against the rule or the page it named, so this reads the pages a visitor sees first against both:
 *
 * - **delivery**: every «free delivery» a page writes is the cart's rule (`Totals`) — qualified by its $35, or
 *   Haul Plus's, whose waiver holds on any order — and every benefit Plus lists is a rule the store applies;
 * - **counts**: the sale's figures on the hero are the deals page's own — its count, its categories, its deepest
 *   badge — counted here card by card, and no page writes an abbreviated count it did not draw from data;
 * - **banners**: every banner opens what it names — «Tech week · Computers», «From $399» a page of computers whose cheapest
 *   is $399 and change — and a banner this test cannot read fails it rather than going unchecked.
 */
class PromisesAgreeTest {
    private val maya by lazy {
        ShildikHarness.accessToken(ShildikHarness.person("Maya Kowalski", SampleCustomers.MAYA))
    }
    private val sam by lazy {
        ShildikHarness.accessToken(ShildikHarness.person("Sam Ortiz", SampleCustomers.SAM))
    }

    private fun store(block: suspend HttpClient.() -> Unit) = haulTest(signIn = ShildikHarness.signIn, block = block)

    @Test
    fun `every delivery promise is the cart's rule`() =
        store {
            // The rule the words are read against (research D7): free from $35 of items, $5.99 under it, nothing to a
            // member whatever the order — so «free delivery» without the $35 is true only as Plus's.
            assertEquals(0, Totals.deliveryCents(Totals.FREE_DELIVERY_FROM_CENTS, plus = false))
            assertEquals(Totals.DELIVERY_CENTS, Totals.deliveryCents(Totals.FREE_DELIVERY_FROM_CENTS - 1, plus = false))
            assertEquals(0, Totals.deliveryCents(1, plus = true), "Plus's «free delivery on every order»")

            val pages =
                mapOf(
                    "a guest's home" to tree("/ui/home"),
                    "Maya's home" to signedIn("/ui/home", maya),
                    "Sam's account" to signedIn("/ui/account", sam),
                    "the deals" to tree("/ui/deals"),
                    "the product" to tree("/ui/p/${SampleCatalog.SONORA_HEADPHONES}"),
                    "an empty cart" to cart(guest()),
                )
            val threshold = "$" + Totals.FREE_DELIVERY_FROM_CENTS / 100
            var promises = 0
            var plusPromises = 0
            pages.forEach { (page, tree) ->
                tree.texts().filter { FREE_DELIVERY.containsMatchIn(it.text) }.forEach { promise ->
                    val text = promise.text
                    promises++
                    if (promise.isPlus()) {
                        plusPromises++
                    } else {
                        assertTrue(
                            Regex("""(over|from) \$threshold\b""").containsMatchIn(text),
                            "$page promises «$text» outside Haul Plus without the cart's $threshold",
                        )
                    }
                    MONEY.findAll(text).map { it.value }.filter { !promise.isPlus() }.forEach {
                        assertEquals(threshold, it, "$page names a delivery threshold the cart does not apply: «$text»")
                    }
                }
            }
            // Positive control: the header's promise on every page, and Plus's on the homes and the account.
            assertTrue(promises >= pages.size + 3, "found only $promises delivery promises")
            assertTrue(plusPromises >= 3, "found only $plusPromises of Plus's promises")
        }

    /** Each benefit Plus lists, on the home's block for a guest and a member and in the trial's dialog, is checked. */
    @Test
    fun `every benefit Plus lists is a rule the store applies`() =
        store {
            val listed =
                (tree("/ui/home").all() + signedIn("/ui/home", maya).all())
                    .filterIsInstance<PlusBlock>()
                    .flatMap { it.benefits } +
                    PlusOffer.dialog.benefits.flatMap { listOf(it.title, it.detail) }
            assertTrue(listed.size >= 6, "the control: the blocks and the dialog list benefits — $listed")
            val sale = SampleCatalog.campaigns.first()
            val earlyAccess = Duration.between(assertNotNull(sale.plusEarlyAccessAt), sale.startsAt)
            val order =
                Totals(itemsCents = 10_000, productDiscountCents = 0, promoCents = 0, deliveryCents = 0, counted = 1)
            val rules: Map<String, () -> Unit> =
                mapOf(
                    "Free delivery on every order" to { assertEquals(0, Totals.deliveryCents(1, plus = true)) },
                    "No minimum basket" to { assertEquals(0, Totals.deliveryCents(1, plus = true)) },
                    "Double points" to { assertEquals(2 * order.points(plus = false), order.points(plus = true)) },
                    "Double points on every purchase" to {
                        assertEquals(2 * order.points(plus = false), order.points(plus = true))
                    },
                    "2 points for every dollar" to { assertEquals(200, order.points(plus = true)) },
                    "Early access to sales" to { assertTrue(earlyAccess > Duration.ZERO) },
                    "Campaign prices 24 hours early" to { assertEquals(Duration.ofHours(24), earlyAccess) },
                )
            listed.distinct().forEach { benefit ->
                val rule = rules[benefit] ?: fail("Plus promises «$benefit», a rule nobody checked")
                rule()
            }
        }

    @Test
    fun `the sale's figures are the deals page's`() =
        store {
            val home = tree("/ui/home")
            val hero = home.only<CampaignHero>()
            val (items, categories) =
                assertNotNull(
                    Regex("""^([\d,]+) items marked down across (\d+) categories$""").find(hero.subtitle),
                    hero.subtitle,
                ).destructured
            val deepest = assertNotNull(Regex("""^Up to (−\d+%)$""").find(hero.title), hero.title).groupValues[1]

            // The deals page, card by card: what it counts and what it holds.
            val first = tree("/ui/deals")
            val counted =
                first
                    .all()
                    .filterIsInstance<PageTitle>()
                    .single()
                    .count
            val onSale = mutableListOf<ProductCard>()
            var page = 1
            do {
                val grid = tree("/ui/deals?page=$page").all().filterIsInstance<ProductGrid>().single { it.id == "grid" }
                onSale += grid.cards
                page++
            } while (grid.cards.isNotEmpty() && onSale.size < 5_000)
            assertEquals("$items items on sale", counted, "the hero and the deals page count apart")
            assertEquals(
                items.replace(",", "").toInt(),
                onSale.distinctBy { it.productId }.size,
                "the deals page's count",
            )

            val seed = CatalogSeed.generate(CatalogSeed.CANVAS_DAY)
            val categoryOf = seed.products.associate { it.id to it.categorySlug }
            val parents = seed.categories.associate { it.slug to it.parentSlug }

            fun top(slug: String): String = parents[slug]?.let(::top) ?: slug
            assertEquals(
                categories.toInt(),
                onSale.map { top(categoryOf.getValue(it.productId)) }.toSet().size,
                "the hero's categories",
            )
            val badges =
                onSale.mapNotNull {
                    it.badge
                        ?.removePrefix("−")
                        ?.removeSuffix("%")
                        ?.toIntOrNull()
                }
            assertEquals(deepest, "−${badges.max()}%", "the hero's deepest discount is not the deepest on sale")

            // The same figure in the empty cart's line, and the categories' count beside their tiles.
            assertEquals(
                "Today’s deals end at midnight — up to $deepest in the Autumn mega sale.",
                cart(guest()).only<EmptyState>().text,
            )
            val tops = seed.categories.count { it.parentSlug == null }
            assertEquals(
                "All $tops categories",
                home
                    .all()
                    .filterIsInstance<SectionHeader>()
                    .single { it.id == "categories-title" }
                    .linkLabel,
            )
            // No abbreviated count — «1.2M», «600K» — on the home or the deals: none is drawn from data there.
            (home.texts() + first.texts()).forEach {
                assertNull(COMPACT.find(it.text), "an abbreviated count nobody counted: «${it.text}»")
            }
        }

    @Test
    fun `every banner opens what its title names`() =
        store {
            val homes = mapOf("a guest" to tree("/ui/home"), "Maya" to signedIn("/ui/home", maya))
            var checked = 0
            homes.forEach { (who, home) ->
                val banners = home.all().filterIsInstance<PromoBanner>()
                assertEquals(2, banners.size, "the control: $who's home draws two banners")
                banners.forEach { banner ->
                    // «Computers from $399», or «From $399» under an eyebrow naming them: «Tech week · Computers».
                    val from = Regex("""^(?:(\w+) f|F)rom \$(\d+)$""").find(banner.title)
                    when {
                        from != null -> {
                            val (titled, price) = from.destructured
                            val named = titled.ifEmpty { banner.eyebrow.substringAfterLast(" · ") }
                            val target =
                                (banner.action as? NavigateAction)?.deeplink ?: fail("«${banner.title}» opens $banner")
                            val page = tree("/ui$target")
                            assertEquals(
                                named,
                                page
                                    .all()
                                    .filterIsInstance<PageTitle>()
                                    .single()
                                    .title,
                                "«${banner.title}» opens $target",
                            )
                            val prices =
                                page
                                    .all()
                                    .filterIsInstance<ProductGrid>()
                                    .single()
                                    .cards
                                    .map { dollars(it.price) }
                            assertTrue(prices.isNotEmpty(), "«${banner.title}» opens an empty page")
                            assertEquals(
                                prices.sorted(),
                                prices,
                                "$target is not cheapest first, so its first card is not its cheapest",
                            )
                            assertTrue(
                                prices.first() >= price.toDouble() && prices.first() < price.toDouble() + 1,
                                "«${banner.title}»: the cheapest at $target is $${prices.first()}",
                            )
                        }

                        banner.eyebrow.contains("Haul Plus") -> {
                            val member = who == "Maya"
                            // Plus's banner is the offer the pill and the block make: sign-in to a guest; to a member,
                            // who is never offered the trial, nothing to press.
                            assertEquals(
                                if (member) null else PlusOffer.trial(Viewer()),
                                banner.action,
                                "$who's «${banner.title}»",
                            )
                        }

                        else -> {
                            fail("$who's banner «${banner.title}» names nothing this test can check against its page")
                        }
                    }
                    checked++
                }
            }
            assertEquals(4, checked)
        }

    private suspend fun HttpClient.signedIn(
        path: String,
        token: String,
    ): KompotComponent {
        val response = get(path) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    /** A string a tree draws, the component it is drawn in and the object holding it — a tile, a benefit. */
    private data class Text(
        val component: JsonObject,
        val text: String,
        val holder: JsonObject = component,
    ) {
        /** Haul Plus's own words: in its block, its dialog, its banner or its account tile. */
        fun isPlus(): Boolean = component.isPlus() || holder.isPlus()
    }

    /** Every string [this] draws — not its ids, addresses, tones or pictures — with the component that draws it. */
    private fun KompotComponent.texts(): List<Text> {
        val texts = mutableListOf<Text>()

        fun walk(
            element: JsonElement,
            component: JsonObject,
        ) {
            when (element) {
                is JsonObject -> {
                    // A component is what has an id: the banners in a campaign row are written without their type.
                    val owner = if ("id" in element) element else component
                    element.forEach { (key, value) ->
                        if (value is JsonPrimitive && value.isString) {
                            if (key !in NOT_COPY) texts += Text(owner, value.content, element)
                        } else {
                            walk(value, owner)
                        }
                    }
                }

                is JsonArray -> {
                    element.forEach { walk(it, component) }
                }

                // A number, a boolean or a null draws no words.
                is JsonPrimitive -> {}
            }
        }
        val json = Json.parseToJsonElement(haulWireJson.encodeKompotComponent(this))
        walk(json, json as JsonObject)
        return texts
    }

    private fun dollars(price: String): Double = price.removePrefix("$").replace(",", "").toDouble()

    private companion object {
        /** A component or an object of one that is Haul Plus's: its type, or a word of it naming Haul Plus. */
        fun JsonObject.isPlus(): Boolean =
            (this["type"] as? JsonPrimitive)?.content in PLUS_TYPES ||
                values.any { it is JsonPrimitive && it.isString && it.content.contains("Haul Plus") }

        val FREE_DELIVERY = Regex("free delivery", RegexOption.IGNORE_CASE)
        val MONEY = Regex("""\$[\d,]+(\.\d\d)?""")
        val COMPACT = Regex("""\b\d+(\.\d)?[KM]\b""")
        val PLUS_TYPES = setOf("haul_plus_block", "haul_plus_trial_dialog")
        val NOT_COPY =
            setOf(
                "type",
                "id",
                "tone",
                "image",
                "photo",
                "deeplink",
                "url",
                "method",
                "category",
                "productId",
                "skuId",
                "share",
                "countdownEndsAt",
                "accent",
                "realtimeTopic",
                "key",
                "initial",
            )
    }
}
