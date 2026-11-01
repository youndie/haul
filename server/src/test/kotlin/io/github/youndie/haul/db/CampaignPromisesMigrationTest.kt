package io.github.youndie.haul.db

import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.PostgresHarness
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * V34 rewrites the seed's campaigns as the seed now writes them (B-172) — the sale's copy naming its figures, Tech
 * week's «From $399» under «Tech week · Computers» with the address it opens, no free-delivery weekend — and this holds the two
 * together: a stand seeded before and migrated must draw the home page a fresh seed draws, or one of them goes on
 * promising «Free delivery on everything» or «Laptops from $399».
 */
class CampaignPromisesMigrationTest {
    @Test
    fun `the campaigns seeded before V34 are migrated to the seed's`() =
        PostgresHarness.freshDatabase(upTo = "32").use { dataSource ->
            dataSource.connection.use { connection ->
                connection
                    .prepareStatement(
                        "INSERT INTO campaigns (slug, name, title, subtitle, position, tone, starts_at, ends_at) " +
                            "VALUES (?, ?, ?, ?, ?, '', now(), now())",
                    ).use { insert ->
                        BEFORE.forEachIndexed { position, (slug, name, title, subtitle) ->
                            insert.setString(1, slug)
                            insert.setString(2, name)
                            insert.setString(3, title)
                            insert.setString(4, subtitle)
                            insert.setInt(5, position)
                            insert.executeUpdate()
                        }
                    }
                connection.commit()
            }

            Databases.migrate(dataSource)

            val migrated =
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement
                            .executeQuery("SELECT slug, name, title, subtitle, link FROM campaigns ORDER BY position")
                            .use { rows ->
                                buildList {
                                    while (rows.next()) {
                                        add((1..5).map { rows.getString(it) })
                                    }
                                }
                            }
                    }
                }
            assertEquals(
                SampleCatalog.campaigns.map { listOf(it.slug, it.name, it.title, it.subtitle, it.link) },
                migrated,
                "V34 and the seed write the campaigns apart",
            )
        }

    private companion object {
        /** The seed's campaigns as V29 left them: slug, name, title, subtitle. */
        val BEFORE =
            listOf(
                listOf(
                    "autumn-mega-sale",
                    "Autumn mega sale",
                    "Up to −70%",
                    "1.2 million items marked down across 32 categories",
                ),
                listOf("tech-week", "Tech week", "Laptops from $399", "Tech week"),
                listOf(
                    "free-delivery-weekend",
                    "Free delivery weekend",
                    "Free delivery on everything",
                    "600K items from local sellers",
                ),
            )
    }
}
