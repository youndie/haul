package io.github.youndie.haul.seed

/**
 * [this] with its campaigns as the seed wrote them before B-172 (V34's «before»): the sale's figures stated, not named
 * («Up to −70%», «1.2 million items…»), Tech week's «Laptops from $399» with no address, and the free-delivery
 * weekend. A catalogue seeded so needs no `campaigns.link`, so it seeds a database migrated to before V34.
 */
internal fun SeedCatalog.campaignsBeforeV34(): SeedCatalog {
    val (sale, tech) = campaigns
    return copy(
        campaigns =
            listOf(
                sale.copy(title = "Up to −70%", subtitle = "1.2 million items marked down across 32 categories"),
                tech.copy(title = "Laptops from $399", subtitle = "Tech week", link = null),
                SeedCampaign(
                    slug = "free-delivery-weekend",
                    name = "Free delivery weekend",
                    title = "Free delivery on everything",
                    subtitle = "600K items from local sellers",
                    position = 2,
                    tone = "#DFFF3A",
                    startsAt = tech.endsAt.minusDays(2),
                    endsAt = tech.endsAt,
                    plusEarlyAccessAt = null,
                ),
            ),
    )
}
