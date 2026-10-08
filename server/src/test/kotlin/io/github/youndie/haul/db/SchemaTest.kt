package io.github.youndie.haul.db

import io.github.youndie.haul.feature.cart.data.cartTables
import io.github.youndie.haul.feature.catalog.data.catalogTables
import io.github.youndie.haul.feature.checkout.data.checkoutTables
import io.github.youndie.haul.feature.order.data.orderTables
import io.github.youndie.haul.feature.payment.data.paymentTables
import io.github.youndie.haul.feature.search.data.searchTables
import io.github.youndie.haul.testing.PostgresHarness
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * V1 is hand-written SQL and the tables are Exposed declarations; this is the only thing that says
 * they agree. A column missing from the migration otherwise surfaces as the first request that reads
 * it failing, on whichever screen gets there first.
 */
class SchemaTest {
    /**
     * V3's search indexes on `products` are over expressions (a `tsvector`, `lower(title)` with trigram
     * ops) that Exposed cannot declare, so it proposes dropping them; they belong to the migration and
     * to the search repository, which writes the same expressions. Only these two are let through.
     */
    private val searchIndexesOnProducts =
        setOf("DROP INDEX IF EXISTS products_search", "DROP INDEX IF EXISTS products_title_trgm")

    @Test
    fun `the migrated schema needs no further DDL for the catalog tables`() {
        val database = Databases.connect(PostgresHarness.freshDatabase())
        val required =
            transaction(database) {
                MigrationUtils.statementsRequiredForDatabaseMigration(*catalogTables.toTypedArray())
            }.filterNot { it in searchIndexesOnProducts }
        assertEquals(
            emptyList(),
            required,
            "the migration and the Exposed tables disagree; still required:\n" + required.joinToString("\n"),
        )
    }

    /** V3's `recent_searches` against its Exposed declaration. */
    @Test
    fun `the migrated schema needs no further DDL for the search tables`() {
        val database = Databases.connect(PostgresHarness.freshDatabase())
        val required =
            transaction(database) {
                MigrationUtils.statementsRequiredForDatabaseMigration(*searchTables.toTypedArray())
            }
        assertEquals(emptyList(), required, "still required:\n" + required.joinToString("\n"))
        assertEquals(1, searchTables.size)
    }

    /** V4's guests, promo codes, carts and lines, and V8's customers, against their Exposed declarations. */
    @Test
    fun `the migrated schema needs no further DDL for the cart tables`() {
        val database = Databases.connect(PostgresHarness.freshDatabase())
        val required =
            transaction(database) {
                MigrationUtils.statementsRequiredForDatabaseMigration(*cartTables.toTypedArray())
            }
        assertEquals(emptyList(), required, "still required:\n" + required.joinToString("\n"))
        assertEquals(5, cartTables.size)
    }

    /** V9's addresses, pickup points, windows, reservations and checkouts against their Exposed declarations. */
    @Test
    fun `the migrated schema needs no further DDL for the checkout tables`() {
        val required =
            PostgresHarness.freshDatabase().use {
                transaction(Databases.connect(it)) {
                    MigrationUtils.statementsRequiredForDatabaseMigration(*checkoutTables.toTypedArray())
                }
            }
        assertEquals(emptyList(), required, "still required:\n" + required.joinToString("\n"))
        assertEquals(5, checkoutTables.size)
    }

    /**
     * V10's tables against their Exposed declarations: petich's three, which petich declares and V10
     * writes by hand (petich ships no DDL), and placement's own.
     */
    @Test
    fun `the migrated schema needs no further DDL for the order and payment tables`() {
        val tables = orderTables + paymentTables
        val required =
            PostgresHarness.freshDatabase().use {
                transaction(Databases.connect(it)) {
                    MigrationUtils.statementsRequiredForDatabaseMigration(*tables.toTypedArray())
                }
            }
        assertEquals(emptyList(), required, "still required:\n" + required.joinToString("\n"))
        assertEquals(8, tables.size)
    }

    /** The guard on the guard: an empty table list also needs no DDL. */
    @Test
    fun `the schema test is looking at every catalog table`() {
        assertEquals(6, catalogTables.size)
    }
}
