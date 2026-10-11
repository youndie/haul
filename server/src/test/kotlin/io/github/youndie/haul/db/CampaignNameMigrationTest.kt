package io.github.youndie.haul.db

import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.PostgresHarness
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * V29 names a campaign written before it after its slug (B-107), and the seed names its campaigns explicitly
 * (`SampleCatalog.campaigns`); this holds the two together — a stand migrated from V28 and a fresh seed must call
 * the sale the same, or the hero and the empty cart of one of them say another name.
 */
class CampaignNameMigrationTest {
    @Test
    fun `a campaign written before V29 is named as the seed names it`() =
        PostgresHarness.freshDatabase(upTo = "28").use { dataSource ->
            dataSource.connection.use { connection ->
                connection
                    .prepareStatement(
                        "INSERT INTO campaigns (slug, title, subtitle, position, tone, starts_at, ends_at) " +
                            "VALUES (?, '', '', ?, '', now(), now())",
                    ).use { insert ->
                        SampleCatalog.campaigns.forEach {
                            insert.setString(1, it.slug)
                            insert.setInt(2, it.position)
                            insert.executeUpdate()
                        }
                    }
                connection.commit()
            }

            Databases.migrate(dataSource)

            val names =
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT slug, name FROM campaigns").use { rows ->
                            buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) }
                        }
                    }
                }
            assertEquals(
                SampleCatalog.campaigns.associate {
                    it.slug to it.name
                },
                names,
                "V29 and the seed name the campaigns apart",
            )
        }
}
