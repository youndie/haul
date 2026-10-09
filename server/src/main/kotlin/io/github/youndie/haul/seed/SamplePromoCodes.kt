package io.github.youndie.haul.seed

import java.time.OffsetDateTime

/**
 * The promo codes of research §6; `Cart_PromoApplied` and `Cart_PromoError` draw these two. The windows are the
 * canvas's; a code that names its campaign moves with that campaign's sale (`CatalogSeed.generate`, B-61).
 */
internal object SamplePromoCodes {
    val all: List<SeedPromoCode> =
        listOf(
            // 10 % off items, up to $50, valid 2025-10-07 … 14: the Autumn mega sale's code, its window the sale's.
            SeedPromoCode(
                code = "AUTUMN10",
                percentOff = 10,
                capCents = 5_000,
                startsAt = OffsetDateTime.parse("2025-10-07T00:00:00-04:00"),
                endsAt = OffsetDateTime.parse("2025-10-15T00:00:00-04:00"),
                campaignSlug = SampleCatalog.AUTUMN_MEGA_SALE,
            ),
            // Expired 2025-08-31: no sale's code, so it keeps the canvas's window and is expired on a stand's day too.
            SeedPromoCode(
                code = "SUMMER5",
                percentOff = 5,
                capCents = null,
                startsAt = OffsetDateTime.parse("2025-08-01T00:00:00-04:00"),
                endsAt = OffsetDateTime.parse("2025-09-01T00:00:00-04:00"),
                campaignSlug = null,
            ),
        )
}
