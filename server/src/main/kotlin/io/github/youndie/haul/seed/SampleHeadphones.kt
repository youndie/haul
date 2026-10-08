package io.github.youndie.haul.seed

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * The headphones the category artboards show (`Catalog_Content`), plus the Marshall pair the empty
 * state is drawn with: the rows feature-browse's scenarios filter. Prices, ratings and review counts
 * are the canvas's; kinds, features and colours are what its chips and facets offer.
 */
internal object SampleHeadphones {
    const val BOSE_STORE = "s-bose-store"
    const val MARSHALL_STORE = "s-marshall-store"

    val sellers: List<SeedSeller> =
        listOf(
            SeedSeller(BOSE_STORE, "Bose Store", BigDecimal("4.8"), 97, 5),
            SeedSeller(MARSHALL_STORE, "Marshall Store", BigDecimal("4.7"), 96, 4),
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
                "bose-qc-ultra",
                "Bose QuietComfort Ultra Headphones",
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
                "sony-wh-ch720n",
                "Sony WH-CH720N Wireless Noise Cancelling",
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
                "bose-qc-sandstone",
                "Bose QuietComfort Headphones, Sandstone",
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
                "sony-ult-wear",
                "Sony ULT Wear Wireless Bass Headphones",
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
                "bose-qc-earbuds-ii",
                "Bose QuietComfort Earbuds II",
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
                "sony-wf-1000xm5",
                "Sony WF-1000XM5 Noise Cancelling Earbuds",
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
                "bose-soundlink-ii",
                "Bose SoundLink Around-Ear Wireless II",
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
                "sony-mdr-7506",
                "Sony MDR-7506 Professional Studio Headphones",
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
                "bose-qc45-refurbished",
                "Bose QuietComfort 45, Refurbished",
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
                "sony-inzone-h9",
                "Sony INZONE H9 Wireless Gaming Headset",
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
                "sony-wh-xb910n",
                "Sony WH-XB910N Extra Bass Noise Cancelling",
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
                "marshall-major-v",
                "Marshall Major V Wireless Headphones",
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
            "Sony" -> SampleCatalog.SONY_STORE
            "Bose" -> BOSE_STORE
            else -> MARSHALL_STORE
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
