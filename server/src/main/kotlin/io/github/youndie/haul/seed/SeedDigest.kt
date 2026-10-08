package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.cart.data.CartLinesTable
import io.github.youndie.haul.feature.cart.data.CartsTable
import io.github.youndie.haul.feature.cart.data.PromoCodesTable
import io.github.youndie.haul.feature.catalog.data.catalogTables
import io.github.youndie.haul.feature.checkout.data.AddressesTable
import io.github.youndie.haul.feature.checkout.data.PickupPointsTable
import io.github.youndie.haul.feature.identity.data.CustomersTable
import io.github.youndie.haul.feature.reviews.data.reviewTables
import java.security.MessageDigest
import javax.sql.DataSource

/**
 * A SHA-256 over every row the seed writes — the catalog, the promo codes, the sample customers,
 * Maya's cart and address, the pickup points, and the reviews and questions — as PostgreSQL prints it:
 * the «same hash twice» of B-03.
 *
 * Read through JDBC as text, not through the Exposed tables, so a column the tables forgot is still
 * in the hash; ordered by the primary key, and in UTC, so neither the physical order of the rows nor
 * the session's time zone moves it.
 */
internal object SeedDigest {
    fun of(dataSource: DataSource): String {
        val sha = MessageDigest.getInstance("SHA-256")
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute("SET TIME ZONE 'UTC'") }
            for (table in catalogTables + PromoCodesTable + CustomersTable + CartsTable + CartLinesTable +
                PickupPointsTable +
                AddressesTable +
                reviewTables) {
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT * FROM ${table.tableName} ORDER BY 1").use { rows ->
                        val columns = rows.metaData.columnCount
                        sha.update("${table.tableName}\n".toByteArray())
                        while (rows.next()) {
                            val row = (1..columns).joinToString("\u0001") { rows.getString(it) ?: "\u0000" }
                            sha.update("$row\n".toByteArray())
                        }
                    }
                }
            }
            connection.rollback()
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }
}
