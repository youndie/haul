package io.github.youndie.haul.seed

import io.github.youndie.haul.seed.SampleCatalog.DUVET_COVER
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES

/**
 * Maya's Saved list (B-20): research §6's 48 saved products, 6 of them cheaper than on the day she saved
 * them — the numbers the account's menu and its Price drops tile draw, now counted from these rows.
 *
 * The canvas's Saved pages draw products the seed does not sell (the robot vacuum, the keyboard, …), so
 * the list is made of what it does: the headphones, saved at their old $449 (now $349, −$100, as on
 * `Saved_PriceDrops`), and the duvet cover set at its old $179 (now $139, −$40); the Bose and the Sony
 * studio headphones the canvas shows at their price; and generated products picked by a fixed stride —
 * four discounted ones saved at their old price, the rest at their price. Newest first, six hours apart
 * before the canvas's «now»; the order puts a drop first and the headphones second, as `Saved_Content`.
 * Deals of the day are left out, so the cards that show them (the empty cart's picks) stay as drawn.
 */
internal object SampleSaved {
    fun mayas(
        products: List<SeedProduct>,
        skus: List<SeedSku>,
        deals: List<SeedDeal>,
    ): List<SeedSaved> {
        val byProduct = skus.groupBy { it.productId }
        val dealt = deals.map { it.skuId.substringBeforeLast('-') }.toSet()

        /** The SKU a card shows: the cheapest in stock, or the cheapest at all (`Listed.shown`). */
        fun shown(productId: String): SeedSku {
            val all = byProduct.getValue(productId)
            return all.filter { it.stock > 0 }.minByOrNull { it.priceCents } ?: all.minBy { it.priceCents }
        }

        val candidates =
            products
                .filter { GENERATED.matches(it.id) && it.id !in dealt && shown(it.id).stock > 0 }
                .filterIndexed { index, _ -> index % STRIDE == 0 }
        val (discounted, plain) = candidates.partition { shown(it.id).oldPriceCents != null }
        val drops = discounted.take(GENERATED_DROPS).map { it.id to checkNotNull(shown(it.id).oldPriceCents) }
        val kept = plain.take(SAVED - GENERATED_DROPS - NAMED).map { it.id to shown(it.id).priceCents }

        val newestFirst =
            buildList {
                add(drops[0])
                add(SONY_HEADPHONES to SONY_OLD_CENTS)
                addAll(kept.subList(0, 5))
                add(BOSE_QC_ULTRA to shown(BOSE_QC_ULTRA).priceCents)
                addAll(kept.subList(5, 8))
                add(SONY_STUDIO to shown(SONY_STUDIO).priceCents)
                add(DUVET_COVER to DUVET_OLD_CENTS)
                addAll(drops.drop(1))
                addAll(kept.drop(8))
            }
        check(newestFirst.size == SAVED && newestFirst.map { it.first }.toSet().size == SAVED) {
            "Maya's Saved list is not 48 products"
        }
        return newestFirst.mapIndexed { index, (productId, cents) ->
            SeedSaved(
                SampleCustomers.MAYA,
                productId,
                cents,
                CatalogSeed.NOW.minusHours(HOURS_APART * (index + 1L)),
            )
        }
    }

    private const val SAVED = 48
    private const val GENERATED_DROPS = 4

    /** The four products the list names: the headphones, the duvet cover set, the Bose and the studio pair. */
    private const val NAMED = 4
    private const val STRIDE = 17
    private const val HOURS_APART = 6L
    private const val SONY_OLD_CENTS = 44_900
    private const val DUVET_OLD_CENTS = 17_900
    private const val BOSE_QC_ULTRA = "p-bose-qc-ultra"
    private const val SONY_STUDIO = "p-sony-mdr-7506"
    private val GENERATED = Regex("p-\\d{3}-\\d{2}")
}
