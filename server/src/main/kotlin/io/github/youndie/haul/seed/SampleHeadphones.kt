package io.github.youndie.haul.seed

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * The headphones the category artboards show (`Catalog_Content`), plus the Mallard pair the empty
 * state is drawn with: the rows feature-browse's scenarios filter. Prices, ratings and review counts
 * are the canvas's; kinds, features and colours are what its chips and facets offer.
 */
internal object SampleHeadphones {
    const val BELLWAVE_STORE = "s-bellwave-store"
    const val MALLARD_STORE = "s-mallard-store"

    val sellers: List<SeedSeller> =
        listOf(
            SeedSeller(BELLWAVE_STORE, "Bellwave Store", BigDecimal("4.8"), 97, 5),
            SeedSeller(MALLARD_STORE, "Mallard Store", BigDecimal("4.7"), 96, 4),
        )

    private data class Row(
        val slug: String,
        val title: String,
        val price: Int,
        val old: Int?,
        val rating: String,
        val reviews: Int,
        val dispatchDays: Int,
        val tone: String,
        val label: String,
        val kind: String,
        val features: List<String>,
        val colours: List<String> = listOf("Black"),
    )

    private const val NC = "Noise cancelling"
    private const val WIRELESS = "Wireless"
    private const val MIC = "Microphone"
    private const val FOLDABLE = "Foldable"

    private val rows =
        listOf(
            Row(
                "bellwave-hush-ultra-165a",
                "Bellwave Hush Ultra 165A Headphones",
                379,
                429,
                "4.7",
                4_108,
                0,
                "#ECE9E2",
                "headphones",
                "Over-ear",
                listOf(NC, WIRELESS, MIC),
            ),
            Row(
                "sonora-sh-c388n",
                "Sonora SH-C388N Wireless Noise Cancelling",
                98,
                149,
                "4.5",
                6_920,
                0,
                "#E0EEF7",
                "headphones",
                "Over-ear",
                listOf(NC, WIRELESS),
            ),
            Row(
                "bellwave-hush-h597c-sandstone",
                "Bellwave Hush H597C Headphones, Sandstone",
                299,
                349,
                "4.7",
                1_877,
                1,
                "#F1EBDD",
                "headphones",
                "Over-ear",
                listOf(NC, WIRELESS, FOLDABLE),
                listOf("Beige"),
            ),
            Row(
                "sonora-pulse-208t",
                "Sonora Pulse 208T Wireless Bass Headphones",
                148,
                199,
                "4.6",
                1_066,
                0,
                "#E3F5D8",
                "headphones",
                "Over-ear",
                listOf(NC, WIRELESS),
            ),
            Row(
                "bellwave-hush-buds-938a",
                "Bellwave Hush Buds 938A",
                229,
                279,
                "4.6",
                3_540,
                0,
                "#F1E4F5",
                "earbuds",
                "True wireless",
                listOf(NC, WIRELESS),
            ),
            Row(
                "sonora-se-188",
                "Sonora SE-188 Noise Cancelling Earbuds",
                248,
                299,
                "4.7",
                2_903,
                0,
                "#FFE5DD",
                "earbuds",
                "True wireless",
                listOf(NC, WIRELESS),
            ),
            Row(
                "bellwave-roam-580p",
                "Bellwave Roam 580P Around-Ear Wireless",
                129,
                null,
                "4.5",
                812,
                2,
                "#FFF1C9",
                "headphones",
                "Over-ear",
                listOf(WIRELESS, MIC),
            ),
            Row(
                "sonora-monitor-630r",
                "Sonora Monitor 630R Professional Studio Headphones",
                99,
                null,
                "4.8",
                9_215,
                0,
                "#ECE9E2",
                "studio",
                "Studio",
                listOf(FOLDABLE),
            ),
            Row(
                "bellwave-hush120k-refurbished",
                "Bellwave Hush 120K, Refurbished",
                199,
                279,
                "4.4",
                640,
                1,
                "#E0EEF7",
                "headphones",
                "Over-ear",
                listOf(NC, WIRELESS),
            ),
            Row(
                "sonora-arena-519",
                "Sonora Arena 519 Wireless Gaming Headset",
                228,
                299,
                "4.6",
                1_321,
                0,
                "#E6E4FF",
                "gaming",
                "Gaming",
                listOf(NC, WIRELESS, MIC),
            ),
            Row(
                "sonora-sh-x527b",
                "Sonora SH-X527B Extra Bass Noise Cancelling",
                128,
                249,
                "4.5",
                2_450,
                0,
                "#E3F5D8",
                "headphones",
                "Over-ear",
                listOf(NC, WIRELESS),
            ),
            Row(
                "mallard-mode-146",
                "Mallard Mode 146 Wireless Headphones",
                149,
                null,
                "4.6",
                204,
                1,
                "#ECE9E2",
                "headphones",
                "On-ear",
                listOf(WIRELESS, FOLDABLE),
            ),
        )

    private fun brandOf(title: String): String = title.substringBefore(' ')

    private fun sellerOf(brand: String): String =
        when (brand) {
            "Sonora" -> SampleCatalog.SONORA_STORE
            "Bellwave" -> BELLWAVE_STORE
            else -> MALLARD_STORE
        }

    val products: List<SeedProduct> =
        rows.map { row ->
            SeedProduct(
                id = "p-${row.slug}",
                sellerId = sellerOf(brandOf(row.title)),
                categorySlug = "headphones",
                title = row.title,
                brand = brandOf(row.title),
                description = "${row.title}.",
                specifications = CatalogSeed.specifications("Brand" to brandOf(row.title), "Type" to row.kind),
                rating = BigDecimal(row.rating),
                reviewsCount = row.reviews,
                questionsCount = 0,
                tone = row.tone,
                label = row.label,
                createdAt = CatalogSeed.NOW.minusDays(60),
                features = row.features,
                kind = row.kind,
                dispatchDays = row.dispatchDays,
                headline = "${row.kind} sound, the ${brandOf(row.title)} way",
                headlineAccent = "the ${brandOf(row.title)} way",
            )
        }

    val skus: List<SeedSku> =
        rows.flatMap { row ->
            row.colours.mapIndexed { position, colour ->
                SeedSku(
                    id = "p-${row.slug}-$position",
                    productId = "p-${row.slug}",
                    position = position,
                    options = JsonObject(mapOf("colour" to JsonPrimitive(colour))),
                    priceCents = row.price * 100,
                    oldPriceCents = row.old?.let { it * 100 },
                    stock = 30,
                )
            }
        }
}
