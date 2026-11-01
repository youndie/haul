package io.github.youndie.haul.seed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

/**
 * What B-166 took out of the sample data: real companies' names, their products' names and their trademarks, and the
 * invented ones that stand in their place. The seed sold Sony, Bose and Marshall headphones from stores named after
 * them, beside invented reviews; the canvas copy the client draws (the category's brand facet) named JBL, Apple and
 * Sennheiser too. [found] is what `InventedBrandsTest` looks for, so none of them comes back; [old] turns today's
 * catalogue into the one seeded before V31, which `InventedBrandsMigrationTest` migrates.
 */
internal object RealNames {
    /** Every product B-166 renamed, old id to new. */
    val products: Map<String, String> =
        mapOf(
            "p-sony-wh-1000xm6" to "p-sonora-sh-718n",
            "p-bose-qc-ultra" to "p-bellwave-hush-ultra-165a",
            "p-sony-wh-ch720n" to "p-sonora-sh-c388n",
            "p-bose-qc-sandstone" to "p-bellwave-hush-h597c-sandstone",
            "p-sony-ult-wear" to "p-sonora-pulse-208t",
            "p-bose-qc-earbuds-ii" to "p-bellwave-hush-buds-938a",
            "p-sony-wf-1000xm5" to "p-sonora-se-188",
            "p-bose-soundlink-ii" to "p-bellwave-roam-580p",
            "p-sony-mdr-7506" to "p-sonora-monitor-630r",
            "p-bose-qc45-refurbished" to "p-bellwave-hush120k-refurbished",
            "p-sony-inzone-h9" to "p-sonora-arena-519",
            "p-sony-wh-xb910n" to "p-sonora-sh-x527b",
            "p-marshall-major-v" to "p-mallard-mode-146",
        )

    /** Every seller B-166 renamed, old id to new. */
    val sellers: Map<String, String> =
        mapOf(
            "s-sony-official-store" to "s-sonora-audio",
            "s-bose-store" to "s-bellwave-store",
            "s-marshall-store" to "s-mallard-store",
        )

    /** The copy B-166 renamed, old to new: the products' titles and listing name, the sellers, the brands. */
    private val copy: List<Pair<String, String>> =
        listOf(
            "Sony WH-1000XM6 Wireless Noise Cancelling Headphones" to
                "Sonora SH-718N Wireless Noise Cancelling Headphones",
            "WH-1000XM6 Wireless Noise Cancelling Headphones" to "SH-718N Wireless Noise Cancelling Headphones",
            "Bose QuietComfort Ultra Headphones" to "Bellwave Hush Ultra 165A Headphones",
            "Sony WH-CH720N Wireless Noise Cancelling" to "Sonora SH-C388N Wireless Noise Cancelling",
            "Bose QuietComfort Headphones, Sandstone" to "Bellwave Hush H597C Headphones, Sandstone",
            "Sony ULT Wear Wireless Bass Headphones" to "Sonora Pulse 208T Wireless Bass Headphones",
            "Bose QuietComfort Earbuds II" to "Bellwave Hush Buds 938A",
            "Sony WF-1000XM5 Noise Cancelling Earbuds" to "Sonora SE-188 Noise Cancelling Earbuds",
            "Bose SoundLink Around-Ear Wireless II" to "Bellwave Roam 580P Around-Ear Wireless",
            "Sony MDR-7506 Professional Studio Headphones" to "Sonora Monitor 630R Professional Studio Headphones",
            "Bose QuietComfort 45, Refurbished" to "Bellwave Hush 120K, Refurbished",
            "Sony INZONE H9 Wireless Gaming Headset" to "Sonora Arena 519 Wireless Gaming Headset",
            "Sony WH-XB910N Extra Bass Noise Cancelling" to "Sonora SH-X527B Extra Bass Noise Cancelling",
            "Marshall Major V Wireless Headphones" to "Mallard Mode 146 Wireless Headphones",
            "Sony Official Store" to "Sonora Audio",
            "Bose Store" to "Bellwave Store",
            "Marshall Store" to "Mallard Store",
            "Sony" to "Sonora",
            "Bose" to "Bellwave",
            "Marshall" to "Mallard",
            " · LDAC · " to " · LC3 · ",
        )

    /**
     * The names that must not come back, as words: the companies, their product lines and models, and the codec. A
     * product's own words («Wireless», «Headphones») are anybody's; these are somebody's.
     */
    val found: Regex =
        Regex(
            listOf(
                "Sony",
                "Bose",
                "Marshall",
                "JBL",
                "Sennheiser",
                "QuietComfort",
                "SoundLink",
                "INZONE",
                "LDAC",
                "WH-1000XM\\d",
                "WF-1000XM\\d",
                "WH-CH720N",
                "WH-XB910N",
                "MDR-7506",
                "ULT Wear",
                "Major V",
            ).joinToString("|", prefix = "\\b(", postfix = ")\\b|(?-i:\\bApple\\b)"),
            RegexOption.IGNORE_CASE,
        )

    /** [text] as the seed wrote it before B-166: every new id and name back to the old one, the longest first. */
    fun old(text: String): String =
        (
            products.entries.map { it.value to it.key } +
                sellers.entries.map { it.value to it.key } +
                listOf("r-sonora-" to "r-sony-", "q-sonora-" to "q-sony-") +
                copy.map { (old, new) -> new to old }
        ).sortedByDescending { it.first.length }
            .fold(text) { acc, (new, old) -> acc.replace(new, old) }

    /** [catalog] as the seed made it before B-166 (V31's «before»). */
    fun old(catalog: SeedCatalog): SeedCatalog =
        catalog.copy(
            sellers = catalog.sellers.map { it.copy(id = old(it.id), name = old(it.name)) },
            products =
                catalog.products.map {
                    it.copy(
                        id = old(it.id),
                        sellerId = old(it.sellerId),
                        title = old(it.title),
                        brand = old(it.brand),
                        description = old(it.description),
                        specifications = Json.parseToJsonElement(old(it.specifications.toString())).jsonArray,
                        headline = old(it.headline),
                        headlineAccent = it.headlineAccent?.let(::old),
                        listingName = it.listingName?.let(::old),
                    )
                },
            skus = catalog.skus.map { it.copy(id = old(it.id), productId = old(it.productId)) },
            deals = catalog.deals.map { it.copy(skuId = old(it.skuId)) },
            carts = catalog.carts.map { cart -> cart.copy(skuIds = cart.skuIds.map(::old)) },
            reviews = catalog.reviews.map { it.copy(id = old(it.id), productId = old(it.productId)) },
            ratingCounts = catalog.ratingCounts.mapKeys { old(it.key) },
            questions = catalog.questions.map { it.copy(id = old(it.id), productId = old(it.productId)) },
            saved = catalog.saved.map { it.copy(productId = old(it.productId)) },
            views = catalog.views.map { it.copy(productId = old(it.productId)) },
        )
}
