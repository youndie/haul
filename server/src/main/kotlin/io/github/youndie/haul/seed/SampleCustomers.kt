package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.membership.domain.PlusMembership
import io.github.youndie.haul.seed.SampleCatalog.DUVET_COVER
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * The two customers of research §6, Maya's cart, and her Haul Plus and points (B-23). A customer's id is
 * the shildik `sub`: a stand whose realm imports these two people with the ids `maya` and `sam` signs them
 * in as these rows. Their orders are the fixture tests' (`SampleOrders`), not the seed's.
 */
internal object SampleCustomers {
    const val MAYA = "maya"
    const val SAM = "sam"

    val all: List<SeedCustomer> =
        listOf(
            SeedCustomer(MAYA, "Maya Kowalski", plus = true),
            SeedCustomer(SAM, "Sam Ortiz", plus = false),
        )

    /**
     * Maya's membership: «Plus since 2023, renews Nov 2» — a trial begun on Oct 3, 2023, paid from Nov 2 and
     * renewing on the 2nd since — with research §6's «$186 saved on delivery» this year carried in, since no
     * seed holds the orders that saved it. Sam has none.
     */
    val memberships: List<PlusMembership> =
        listOf(
            PlusMembership(
                customerId = MAYA,
                startedAt = OffsetDateTime.parse("2023-10-03T10:00:00-04:00"),
                trial = true,
                paidFrom = LocalDate.parse("2023-11-02"),
                carriedSavingsCents = 18_600,
                carriedSavingsYear = 2025,
            ),
        )

    /** Maya's 2,480 points («worth $24.80 on your next order»), earned before the store kept orders. */
    val openingPoints: Map<String, Int> = mapOf(MAYA to 2_480)

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
