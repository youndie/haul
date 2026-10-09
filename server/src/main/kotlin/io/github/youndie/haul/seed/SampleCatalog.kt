package io.github.youndie.haul.seed

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * The catalog half of research §6: the rows every artboard, fixture and scenario names by value.
 * Changing one here changes what the canvas is compared against, so a change starts in research §6.
 */
internal object SampleCatalog {
    const val SONY_STORE = "s-sony-official-store"
    const val BROOKLYN_HOME = "s-brooklyn-home-co"
    const val SONY_HEADPHONES = "p-sony-wh-1000xm6"
    const val DUVET_COVER = "p-linen-duvet-cover-set"
    const val STONEWARE_MUG = "p-stoneware-mug"

    val sellers: List<SeedSeller> =
        listOf(
            SeedSeller(SONY_STORE, "Sony Official Store", BigDecimal("4.9"), 98, 6),
            SeedSeller(BROOKLYN_HOME, "Brooklyn Home Co.", BigDecimal("4.8"), 97, 3),
        ) + SampleHeadphones.sellers

    /** The categories the sample products live in; every other top-level category gets generic ones. */
    val subcategories: Map<String, List<Pair<String, List<String>>>> =
        mapOf(
            "electronics" to
                listOf(
                    "Audio" to listOf("Headphones", "Speakers"),
                    "Video" to listOf("TVs", "Projectors"),
                ),
            "home-kitchen" to
                listOf("Bedding" to listOf("Duvet covers", "Pillows"), "Kitchen" to listOf("Mugs", "Cookware")),
            // feature-search's «Typing suggests» looks for «Sports › Running shoes».
            "sports" to
                listOf(
                    "Running gear" to listOf("Running shoes", "Running jackets"),
                    "Fitness" to listOf("Yoga mats", "Dumbbells"),
                ),
        )

    private val created: OffsetDateTime = CatalogSeed.NOW.minusDays(90)

    val products: List<SeedProduct> =
        listOf(
            SeedProduct(
                id = SONY_HEADPHONES,
                sellerId = SONY_STORE,
                categorySlug = "headphones",
                title = "WH-1000XM6 Wireless Noise Cancelling Headphones",
                brand = "Sony",
                description = "Adaptive noise cancelling with twelve microphones, up to 40 hours on a charge.",
                specifications =
                    CatalogSeed.specifications(
                        "Battery" to "Up to 40 hours, 3 min charge = 3 h",
                        "Noise cancelling" to "Adaptive, 12 microphones",
                        "Connectivity" to "Bluetooth 5.4 · LDAC · multipoint",
                        "Weight" to "250 g",
                    ),
                rating = BigDecimal("4.8"),
                reviewsCount = 2_341,
                questionsCount = 86,
                tone = "#E6E4FF",
                label = "headphones",
                createdAt = created,
                features = listOf("Noise cancelling", "Wireless", "Microphone"),
                kind = "Over-ear",
                // Product_Description's headline, the accent in Bodoni italic.
                headline = "Silence, tuned to you",
                headlineAccent = "to you",
                // «12K bought this month», as every Product_* artboard writes it (B-52).
                boughtBase = 12_340,
                // Cards, the cart and the order write the brand into the name; the product page writes it above
                // the title (B-45). V25 gives a catalogue seeded before it the same name.
                listingName = "Sony WH-1000XM6 Wireless Noise Cancelling Headphones",
            ),
            SeedProduct(
                id = DUVET_COVER,
                sellerId = BROOKLYN_HOME,
                categorySlug = "duvet-covers",
                title = "Linen Duvet Cover Set, Queen",
                brand = "Brooklyn Home Co.",
                description = "Stonewashed linen, a duvet cover and two pillowcases.",
                specifications = CatalogSeed.specifications("Material" to "100 % linen", "Size" to "Queen"),
                rating = BigDecimal("4.7"),
                reviewsCount = 1_388,
                questionsCount = 12,
                tone = "#F1EBDD",
                label = "bedding",
                createdAt = created,
                dispatchDays = 1,
                headline = "Linen that softens with every wash",
                headlineAccent = "every wash",
                // «2.1K bought this month»: no artboard draws its page.
                boughtBase = 2_180,
            ),
            SeedProduct(
                id = STONEWARE_MUG,
                sellerId = BROOKLYN_HOME,
                categorySlug = "mugs",
                title = "Stoneware Mug, 12 oz",
                brand = "Brooklyn Home Co.",
                description = "Hand-glazed stoneware, dishwasher safe.",
                specifications = CatalogSeed.specifications("Capacity" to "12 oz", "Material" to "Stoneware"),
                rating = BigDecimal("4.8"),
                reviewsCount = 420,
                questionsCount = 4,
                tone = "#E3F5D8",
                label = "mug",
                createdAt = created,
                dispatchDays = 1,
                headline = "Glazed by hand, one at a time",
                headlineAccent = "by hand",
                // «840 bought this month», the backlog item's own example.
                boughtBase = 840,
            ),
        ) + SampleHeadphones.products

    val skus: List<SeedSku> =
        sonySkus() +
            listOf(
                SeedSku("$DUVET_COVER-0", DUVET_COVER, 0, options("Oat", "3 pieces"), 13_900, 17_900, 40),
                SeedSku("$STONEWARE_MUG-0", STONEWARE_MUG, 0, options("Sage", "Set of 2"), 2_400, null, 120),
            ) + SampleHeadphones.skus

    /** Midnight Black in three bundles, and Silver — out of stock in all three (`Product_OutOfStock`). */
    private fun sonySkus(): List<SeedSku> {
        val bundles =
            listOf(
                "Headphones only" to (34_900 to 44_900),
                "+ Travel case" to (37_900 to 47_900),
                "+ 2-year care" to (39_900 to 49_900),
            )
        return listOf("Midnight Black" to 25, "Silver" to 0).flatMapIndexed { c, (colour, stock) ->
            bundles.mapIndexed { b, (bundle, prices) ->
                val position = c * bundles.size + b
                SeedSku(
                    "$SONY_HEADPHONES-$position",
                    SONY_HEADPHONES,
                    position,
                    options(colour, bundle),
                    prices.first,
                    prices.second,
                    stock,
                )
            }
        }
    }

    /** The headphones in Midnight Black, headphones only, are a deal of the day at their $349. */
    val SONY_DEAL: SeedDeal = SeedDeal("deal-1", "$SONY_HEADPHONES-0", 34_900, CatalogSeed.DEALS_END)

    val campaigns: List<SeedCampaign> =
        listOf(
            SeedCampaign(
                slug = "autumn-mega-sale",
                title = "Up to −70%",
                subtitle = "1.2 million items marked down across 32 categories",
                position = 0,
                tone = "#2F2BFF",
                startsAt = OffsetDateTime.parse("2025-10-07T00:00:00-04:00"),
                endsAt = OffsetDateTime.parse("2025-10-15T00:00:00-04:00"),
                plusEarlyAccessAt = OffsetDateTime.parse("2025-10-06T00:00:00-04:00"),
            ),
            SeedCampaign(
                slug = "tech-week",
                title = "Laptops from $399",
                subtitle = "Tech week",
                position = 1,
                tone = "#E6E4FF",
                startsAt = OffsetDateTime.parse("2025-10-06T00:00:00-04:00"),
                endsAt = OffsetDateTime.parse("2025-10-13T00:00:00-04:00"),
                plusEarlyAccessAt = null,
            ),
            SeedCampaign(
                slug = "free-delivery-weekend",
                title = "Free delivery on everything",
                subtitle = "600K items from local sellers",
                position = 2,
                tone = "#DFFF3A",
                startsAt = OffsetDateTime.parse("2025-10-11T00:00:00-04:00"),
                endsAt = OffsetDateTime.parse("2025-10-13T00:00:00-04:00"),
                plusEarlyAccessAt = null,
            ),
        )

    private fun options(
        colour: String,
        bundle: String,
    ): JsonObject = JsonObject(mapOf("colour" to JsonPrimitive(colour), "bundle" to JsonPrimitive(bundle)))
}
