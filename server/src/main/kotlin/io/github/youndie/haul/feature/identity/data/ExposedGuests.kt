package io.github.youndie.haul.feature.identity.data

import io.github.youndie.haul.feature.identity.domain.Guests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

/** Guests over Exposed. An id is `g-` and a random UUID: unguessable, because it is the guest's only credential. */
internal class ExposedGuests(
    private val database: Database,
) : Guests {
    private suspend fun <T> tx(block: () -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    override suspend fun create(at: OffsetDateTime): String =
        tx {
            val id = "g-${UUID.randomUUID()}"
            GuestsTable.insert {
                it[GuestsTable.id] = id
                it[createdAt] = at
            }
            id
        }

    override suspend fun exists(id: String): Boolean =
        tx {
            !GuestsTable.selectAll().where { GuestsTable.id eq id }.empty()
        }
}
