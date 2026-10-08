package io.github.youndie.haul.feature.order.data

import io.github.youndie.haul.feature.order.saga.OrderPayload
import io.github.youndie.haul.feature.order.saga.Refused
import io.github.youndie.petich.EnrichedPayload
import io.github.youndie.petich.PetichPayload
import io.github.youndie.petich.PetichStepRecord
import io.github.youndie.petich.SimpleEnrichedPayload
import io.github.youndie.petich.postgres.IdempotencyKeysTable
import io.github.youndie.petich.postgres.OutboxEventsTable
import io.github.youndie.petich.postgres.PetichTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

// In a file of their own, and that is load-bearing: beside `orderTables` they made one class whose
// initialisation reached `SagaTables` while `SagaTables` was initialising itself through `sagaJson()`,
// and the list was built with nulls in it whenever the saga's tables were touched first.

/**
 * The `Json` the saga's rows are written with, and the one place its polymorphism is registered: a
 * class written into a row and not named here cannot be read back, and the saga holding it cannot be
 * carried on after a restart.
 */
internal fun sagaJson(): Json =
    Json {
        ignoreUnknownKeys = true
        serializersModule =
            SerializersModule {
                polymorphic(PetichPayload::class) { subclass(OrderPayload::class) }
                polymorphic(EnrichedPayload::class) { subclass(SimpleEnrichedPayload::class) }
                polymorphic(PetichStepRecord::class) { subclass(Refused::class) }
            }
    }

/** petich's three tables, declared by petich and created by V10. */
internal object SagaTables {
    val petiches: PetichTable = PetichTable(sagaJson())
    val outbox: OutboxEventsTable = OutboxEventsTable()
    val idempotencyKeys: IdempotencyKeysTable = IdempotencyKeysTable()
}
