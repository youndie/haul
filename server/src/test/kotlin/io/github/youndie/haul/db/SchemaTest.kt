package io.github.youndie.haul.db

import io.github.youndie.haul.feature.catalog.data.catalogTables
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
    @Test
    fun `the migrated schema needs no further DDL for the catalog tables`() {
        val database = Databases.connect(PostgresHarness.freshDatabase())
        val required =
            transaction(database) {
                MigrationUtils.statementsRequiredForDatabaseMigration(*catalogTables.toTypedArray())
            }
        assertEquals(
            emptyList(),
            required,
            "the migration and the Exposed tables disagree; still required:\n" + required.joinToString("\n"),
        )
    }

    /** The guard on the guard: an empty table list also needs no DDL. */
    @Test
    fun `the schema test is looking at every catalog table`() {
        assertEquals(6, catalogTables.size)
    }
}
