package io.github.youndie.haul.seed

import io.github.youndie.haul.seed.SampleCatalog.DUVET_COVER
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG

/**
 * The two customers of research §6 and Maya's cart. A customer's id is the shildik `sub`: a stand
 * whose realm imports these two people with the ids `maya` and `sam` signs them in as these rows.
 * Their points, savings and orders arrive with the features that own them.
 */
internal object SampleCustomers {
    const val MAYA = "maya"
    const val SAM = "sam"

    val all: List<SeedCustomer> =
        listOf(
            SeedCustomer(MAYA, "Maya Kowalski", plus = true),
            SeedCustomer(SAM, "Sam Ortiz", plus = false),
        )

    /** The headphones, the duvet cover set and the mug set, one each, in that order (`Cart_Content`). */
    val carts: List<SeedCart> =
        listOf(
            SeedCart(
                id = "cart-$MAYA",
                customerId = MAYA,
                skuIds = listOf("$SONY_HEADPHONES-0", "$DUVET_COVER-0", "$STONEWARE_MUG-0"),
            ),
        )
}
