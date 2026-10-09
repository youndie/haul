package io.github.youndie.haul.seed

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.membership.data.ExposedMemberships
import io.github.youndie.haul.feature.membership.data.ExposedPointsLedger
import io.github.youndie.haul.feature.membership.data.MembershipsTable
import io.github.youndie.haul.feature.membership.data.PointsEntriesTable
import io.github.youndie.haul.feature.membership.domain.PointsMovement
import io.github.youndie.haul.testing.PostgresHarness
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Maya's Haul Plus and points are seeded once (B-23): a database whose catalog was seeded before V19 — no
 * membership, no points — gets them on its next start, and points spent since are not handed out again.
 */
class SeedLoyaltyTest {
    @Test
    fun `a database seeded before the ledger gets Maya's membership and points once`() {
        val dataSource = PostgresHarness.freshDatabase()
        val database = Databases.connect(dataSource)
        Seeder.seedIfEmpty(database, CatalogSeed.generate(CatalogSeed.CANVAS_DAY))
        transaction(database) {
            PointsEntriesTable.deleteAll()
            MembershipsTable.deleteAll()
        }

        assertFalse(Seeder.seedIfEmpty(database, CatalogSeed.generate(CatalogSeed.CANVAS_DAY)), "the catalog was there")
        val ledger = ExposedPointsLedger(database)
        runBlocking {
            assertEquals(2_480, ledger.balance(SampleCustomers.MAYA))
            assertEquals(
                LocalDate.parse("2023-11-02"),
                ExposedMemberships(database).membership(SampleCustomers.MAYA)?.paidFrom,
            )
            ledger.redeem(PointsMovement.redeemed(SampleCustomers.MAYA, "HL-1", 2_480, CatalogSeed.NOW))
        }

        Seeder.seedIfEmpty(database, CatalogSeed.generate(CatalogSeed.CANVAS_DAY))
        assertEquals(0, runBlocking { ledger.balance(SampleCustomers.MAYA) }, "the opening balance is not seeded twice")
    }
}
