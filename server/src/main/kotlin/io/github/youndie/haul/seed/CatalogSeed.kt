package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * The catalog a fresh database is seeded with: the sample data of research §6, which every fixture
 * and artboard shows, inside a generated catalog of about two thousand products.
 *
 * **Deterministic, and nothing else about it is negotiable.** The same build seeds the same rows on
 * every machine: one [Random] with a fixed seed, consumed in one fixed order, and no clock — every
 * instant is derived from [NOW], the canvas's «now». A seed that differs between two runs makes every
 * screenshot and every scenario that reads it a coin toss.
 *
 * **The one exception is the sale's calendar** (B-58): the campaigns' and the deals' windows are the
 * canvas's, moved to the day [generate] is given — the canvas's day [CANVAS_DAY] in every test, so each row
 * is the canvas's, and the store's day on a stand, so a stand seeded today opens the Autumn mega sale and
 * the deals of the day today, as the canvas does on Oct 7. Only the windows move; prices, products and every
 * other date stay the canvas's.
 */
internal object CatalogSeed {
    /** 2025-10-07 19:47:23 in New York, a Tuesday: the moment every artboard shows (research §1.6). */
    val NOW: OffsetDateTime = OffsetDateTime.parse("2025-10-07T19:47:23-04:00")

    /** Deals of the day open at the canvas's day's local midnight (B-57)… */
    val DEALS_START: OffsetDateTime = OffsetDateTime.parse("2025-10-07T00:00:00-04:00")

    /** …and end at the next (research D7). */
    val DEALS_END: OffsetDateTime = OffsetDateTime.parse("2025-10-08T00:00:00-04:00")

    /** The canvas's day in the store's zone, Oct 7, 2025: the day the tests seed the sale for (B-58). */
    val CANVAS_DAY: LocalDate = NOW.atZoneSameInstant(DeliveryCalendar.STORE).toLocalDate()

    /** The store's day at [now]: the day a stand seeds the sale for. */
    fun dayOf(now: ZonedDateTime): LocalDate = now.withZoneSameInstant(DeliveryCalendar.STORE).toLocalDate()

    private const val RANDOM_SEED = 20251007
    private const val PRODUCTS_PER_LEAF = 15
    private const val GENERATED_SELLERS = 38

    /** The catalog, its sale dated from [day] (the canvas's day: [CANVAS_DAY]). */
    fun generate(day: LocalDate): SeedCatalog {
        val random = Random(RANDOM_SEED)
        val categories = categories()
        val sellers = SampleCatalog.sellers + generatedSellers(random)
        val products = mutableListOf<SeedProduct>()
        val skus = mutableListOf<SeedSku>()
        SampleCatalog.products.forEach { products += it }
        SampleCatalog.skus.forEach { skus += it }

        val leaves = categories.filter { leaf -> categories.none { it.parentSlug == leaf.slug } }
        leaves.forEachIndexed { leafIndex, leaf ->
            val fixtures = SampleCatalog.products.count { it.categorySlug == leaf.slug }
            repeat(PRODUCTS_PER_LEAF - fixtures) { n ->
                val id = "p-%03d-%02d".format(leafIndex, n)
                val (product, productSkus) = generatedProduct(random, id, leaf, sellers)
                products += product
                skus += productSkus
            }
        }

        val deals =
            (
                listOf(SampleCatalog.SONY_DEAL) +
                    DEAL_PICKS.mapIndexed { i, pick ->
                        val sku = skus.first { it.productId == products[pick].id }
                        SeedDeal("deal-${i + 2}", sku.id, sku.priceCents * DEAL_PERCENT / 100, DEALS_START, DEALS_END)
                    }
            ).map { it.copy(startsAt = it.startsAt.on(day), endsAt = it.endsAt.on(day)) }
        val campaigns =
            SampleCatalog.campaigns.map {
                it.copy(
                    startsAt = it.startsAt.on(day),
                    endsAt = it.endsAt.on(day),
                    plusEarlyAccessAt = it.plusEarlyAccessAt?.on(day),
                )
            }
        return SeedCatalog(
            categories,
            sellers,
            products,
            inCampaigns(skus),
            campaigns,
            deals,
            SamplePromoCodes.all,
            SampleCustomers.all,
            SampleCustomers.carts,
            SampleCheckout.pickupPoints,
            SampleCheckout.addresses,
            SampleReviews.reviews,
            SampleReviews.ratingCounts,
            SampleReviews.questions,
            SampleSaved.mayas(products, skus, deals),
            memberships = SampleCustomers.memberships,
            openingPoints = SampleCustomers.openingPoints,
            views = SampleViews.mayas,
        )
    }

    /**
     * This canvas instant on [day]: moved by the whole store days between [CANVAS_DAY] and [day] at the same
     * local time, so a midnight stays the store's midnight across a change of clocks (the canvas's October
     * offset is −04:00, a January day's −05:00). On the canvas's day it is the instant itself.
     */
    private fun OffsetDateTime.on(day: LocalDate): OffsetDateTime {
        val days = ChronoUnit.DAYS.between(CANVAS_DAY, day)
        if (days == 0L) return this
        return atZoneSameInstant(DeliveryCalendar.STORE).plusDays(days).toOffsetDateTime()
    }

    /**
     * Every markdown of the seed is the Autumn mega sale's — its hero says so, «1.2 million items marked
     * down» — so a SKU whose old price is above its price is that campaign's (B-53): a Plus member sees it
     * from the sale's early-access start, everybody else from its start. Applied after generation, so the
     * random stream and every other row stay as they were.
     */
    private fun inCampaigns(skus: List<SeedSku>): List<SeedSku> =
        skus.map { sku ->
            val markdown = sku.oldPriceCents?.let { it > sku.priceCents } == true
            if (markdown) sku.copy(campaignSlug = SampleCatalog.AUTUMN_MEGA_SALE) else sku
        }

    /** 32 top-level categories, two subcategories each, two leaves under each subcategory. */
    private fun categories(): List<SeedCategory> {
        val all = mutableListOf<SeedCategory>()
        TOP_LEVEL.forEachIndexed { i, name ->
            val top = slugOf(name)
            all += SeedCategory(top, null, name, i, TONES[i % TONES.size], top)
        }
        TOP_LEVEL.forEachIndexed { i, name ->
            val top = slugOf(name)
            val subs =
                SampleCatalog.subcategories[top]
                    ?: GENERIC_SUBS.map { "$name ${it.first}" to it.second.map { leaf -> "$name ${it.first} $leaf" } }
            subs.forEachIndexed { s, (subName, leaves) ->
                val sub = slugOf(subName)
                all += SeedCategory(sub, top, subName.removePrefix("$name "), s, TONES[(i + s) % TONES.size], sub)
                leaves.forEachIndexed { l, leafName ->
                    val leaf = slugOf(leafName)
                    all +=
                        SeedCategory(
                            leaf,
                            sub,
                            leafName.removePrefix("$subName "),
                            l,
                            TONES[(i + s + l) % TONES.size],
                            leaf,
                        )
                }
            }
        }
        return all
    }

    private fun generatedSellers(random: Random): List<SeedSeller> =
        (1..GENERATED_SELLERS).map { n ->
            SeedSeller(
                id = "s-%02d".format(n),
                name = "${SELLER_WORDS[n % SELLER_WORDS.size]} ${SELLER_NOUNS[n % SELLER_NOUNS.size]} Co.",
                rating = BigDecimal.valueOf(random.nextLong(40, 50), 1),
                positivePercent = random.nextInt(90, 100),
                yearsOnHaul = random.nextInt(1, 9),
            )
        }

    private fun generatedProduct(
        random: Random,
        id: String,
        leaf: SeedCategory,
        sellers: List<SeedSeller>,
    ): Pair<SeedProduct, List<SeedSku>> {
        val brand = BRANDS[random.nextInt(BRANDS.size)]
        val adjective = ADJECTIVES[random.nextInt(ADJECTIVES.size)]
        val (headline, accent) = HEADLINES.getValue(adjective)
        val product =
            SeedProduct(
                id = id,
                sellerId = sellers[random.nextInt(sellers.size)].id,
                categorySlug = leaf.slug,
                title = "$brand $adjective ${leaf.name}",
                brand = brand,
                description = "$adjective ${leaf.name.lowercase()} by $brand.",
                specifications = specifications("Brand" to brand, "Model" to id.uppercase()),
                rating = BigDecimal.valueOf(random.nextLong(38, 50), 1),
                reviewsCount = random.nextInt(0, 12_000),
                questionsCount = random.nextInt(0, 200),
                tone = TONES[random.nextInt(TONES.size)],
                label = leaf.label,
                createdAt = NOW.minusDays(random.nextLong(1, 720)),
                headline = headline,
                headlineAccent = accent,
            )
        val price = random.nextInt(5, 500) * 100 + if (random.nextBoolean()) 99 else 0
        val discounted = random.nextInt(100) < DISCOUNTED_PERCENT
        val skus =
            (0 until random.nextInt(1, 4)).map { position ->
                SeedSku(
                    id = "$id-$position",
                    productId = id,
                    position = position,
                    options = JsonObject(mapOf("colour" to JsonPrimitive(COLOURS[(position + price) % COLOURS.size]))),
                    priceCents = price,
                    oldPriceCents = if (discounted) price + price * random.nextInt(10, 50) / 100 else null,
                    stock = if (random.nextInt(100) < OUT_OF_STOCK_PERCENT) 0 else random.nextInt(1, 200),
                )
            }
        return product to skus
    }

    internal fun specifications(vararg pairs: Pair<String, String>): JsonArray =
        JsonArray(
            pairs.map { (key, value) ->
                JsonObject(
                    mapOf(
                        "key" to JsonPrimitive(key),
                        "value" to JsonPrimitive(value),
                    ),
                )
            },
        )

    private fun slugOf(name: String): String =
        name
            .lowercase()
            .replace("&", "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    private const val DISCOUNTED_PERCENT = 40
    private const val OUT_OF_STOCK_PERCENT = 5
    private const val DEAL_PERCENT = 70

    /** Generated products made deals of the day beside the hero headphones; indices into the product list. */
    private val DEAL_PICKS = listOf(40, 400, 800, 1200, 1600)

    private val TOP_LEVEL =
        listOf(
            "Electronics",
            "Home & Kitchen",
            "Fashion",
            "Beauty",
            "Kids & Toys",
            "Sports",
            "Grocery",
            "Auto",
            "Books",
            "Pets",
            "Garden",
            "Office",
            "Health",
            "Music",
            "Movies",
            "Games",
            "Tools",
            "Baby",
            "Jewelry",
            "Shoes",
            "Luggage",
            "Crafts",
            "Outdoors",
            "Party",
            "Furniture",
            "Lighting",
            "Appliances",
            "Computers",
            "Phones",
            "Cameras",
            "Watches",
            "Bags",
        )

    private val GENERIC_SUBS =
        listOf(
            "Essentials" to listOf("Classics", "New in"),
            "Premium" to listOf("Signature", "Limited"),
        )

    // The canvas's tile tones (canvas.json `tileTones`), so a generated card looks like a drawn one.
    private val TONES =
        listOf("#E6E4FF", "#F1EBDD", "#FFE5DD", "#F1E4F5", "#FFF1C9", "#E3F5D8", "#E9F0D2", "#E0EEF7", "#ECE9E2")

    private val BRANDS =
        listOf(
            "Northwind",
            "Pace",
            "Cloudline",
            "Ridge",
            "Summit",
            "Metro",
            "Oakline",
            "Harbor",
            "Lumen",
            "Fieldnote",
            "Tandem",
            "Kestrel",
        )
    private val ADJECTIVES =
        listOf("Everyday", "Compact", "Classic", "Pro", "Lightweight", "Essential", "Deluxe", "Travel", "Studio", "Eco")

    /**
     * A generated product's description headline and its accent, by the adjective the [Random] drew for
     * it: deterministic like the rest of the product, and drawn from nothing new, so adding headlines
     * (B-33) moved no value the seed drew before them.
     */
    private val HEADLINES =
        mapOf(
            "Everyday" to ("Made for every day" to "every day"),
            "Compact" to ("Small enough to take along" to "take along"),
            "Classic" to ("A classic, done right" to "done right"),
            "Pro" to ("Works as hard as you do" to "as you do"),
            "Lightweight" to ("Light enough to forget" to "to forget"),
            "Essential" to ("The one you reach for first" to "reach for first"),
            "Deluxe" to ("A little more of everything" to "of everything"),
            "Travel" to ("Packed for the road" to "the road"),
            "Studio" to ("Tuned in the studio" to "the studio"),
            "Eco" to ("Kind to the planet, too" to "the planet"),
        )
    private val COLOURS = listOf("Graphite", "Oat", "Sage", "Midnight", "Sand", "Cobalt")
    private val SELLER_WORDS = listOf("Hudson", "Bedford", "Wythe", "Kent", "Grand", "Union", "Bushwick", "Greenpoint")
    private val SELLER_NOUNS = listOf("Goods", "Supply", "Trading", "Outfitters", "Market", "House", "Works")
}
