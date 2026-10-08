package io.github.youndie.haul.feature.identity.data

import io.github.youndie.haul.feature.identity.domain.Customer
import io.github.youndie.haul.feature.identity.domain.Customers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime

/** Customers over Exposed. Two first requests at once both insert; the primary key lets one through. */
internal class ExposedCustomers(
    private val database: Database,
) : Customers {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun signedIn(
        id: String,
        name: String,
        at: OffsetDateTime,
    ): Customer =
        tx {
            CustomersTable.insertIgnore {
                it[CustomersTable.id] = id
                it[CustomersTable.name] = name
                it[plus] = false
                it[createdAt] = at
            }
            CustomersTable.selectAll().where { CustomersTable.id eq id }.single().let {
                Customer(
                    it[CustomersTable.id],
                    it[CustomersTable.name],
                    it[CustomersTable.plus],
                    it[CustomersTable.createdAt],
                )
            }
        }
}
