package io.github.youndie.haul.seed

import io.github.youndie.haul.seed.SampleCatalog.DUVET_COVER
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG

/**
 * Maya's product views (B-25), so the stand's home page draws her `Home_Content` with «Picked for you —
 * Based on your recent views» rather than the popular row of a customer who has viewed nothing.
 * feature-recommendations' scenario «From views» as rows: three headphones — the pair in her cart, the Bose
 * and the Sony studio pair — and the other two products of her cart, newest first, an hour apart before the
 * canvas's «now». Her picks are then two headphones, two duvet covers and two mugs, none of them these.
 */
internal object SampleViews {
    val mayas: List<SeedView> =
        listOf(SONY_HEADPHONES, "p-bose-qc-ultra", "p-sony-mdr-7506", DUVET_COVER, STONEWARE_MUG)
            .mapIndexed { index, productId ->
                SeedView(SampleCustomers.MAYA, productId, CatalogSeed.NOW.minusHours(index + 1L))
            }
}
