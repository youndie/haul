package io.github.youndie.haul.feature.order.saga

import io.github.youndie.haul.db.ConnectionPatience
import io.github.youndie.haul.feature.order.data.SagaTables
import io.github.youndie.petich.AnnouncementFailureHandler
import io.github.youndie.petich.CompensationFailureHandler
import io.github.youndie.petich.LinePetichTracer
import io.github.youndie.petich.OutboxEvent
import io.github.youndie.petich.Petich
import io.github.youndie.petich.PetichClock
import io.github.youndie.petich.PetichDefinition
import io.github.youndie.petich.PetichEngine
import io.github.youndie.petich.PetichEngineConfig
import io.github.youndie.petich.PetichPhase
import io.github.youndie.petich.SuspendedPetichSweeper
import io.github.youndie.petich.postgres.ExposedIdempotencyRepository
import io.github.youndie.petich.postgres.ExposedPetichRepository
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("io.github.youndie.haul.saga")

/**
 * petich's storage over the application's database, stamped by one [clock]: the store's `updated_at`
 * and the sweeper's «stuck since» are compared against each other, so they must come from the same
 * clock (shashki B-93: two clocks years apart and the sweeper matched nothing, silently).
 */
internal class SagaStorage(
    database: Database,
    clock: PetichClock,
) {
    val sagas: ExposedPetichRepository =
        ExposedPetichRepository(database, SagaTables.petiches, SagaTables.outbox, clock)
    val idempotencyKeys: ExposedIdempotencyRepository =
        ExposedIdempotencyRepository(database, SagaTables.idempotencyKeys, clock)
}

/**
 * The engine, with the two refusals that are decisions rather than defaults: a rollback that gives up
 * and an announcement that could not be made both go to the log at the level a person reads, and an
 * engine built without somewhere to say them is refused at construction.
 */
internal fun orderEngine(
    storage: SagaStorage,
    clock: PetichClock,
    definitions: List<PetichDefinition<*>>,
): PetichEngine =
    PetichEngine(
        repository = storage.sagas,
        compensationFailureHandler = CompensationFailures,
        config = ENGINE_CONFIG,
        clock = clock,
        definitions = definitions,
        announcementFailureHandler = AnnouncementFailures,
        // One `petich.trace` line per saga event: which member a saga died in is otherwise in nobody's log.
        tracer = LinePetichTracer("haul", clock) { log.debug(it) },
    )

/** petich's phase timeouts, its defaults, and the two handlers required. */
private val ENGINE_CONFIG =
    PetichEngineConfig(requireCompensationHandler = true, requireAnnouncementFailureHandler = true)

/**
 * How long saga work waits for one connection it cannot have (B-139, [ConnectionPatience]): the timeout of the
 * phase every member of the order saga runs in, EXECUTION's 10 s — each is a `step`, the card's authorisation
 * included, so none runs under AUTHORIZATION's 30 s. A member is cut at that timeout anyway; this bounds what no
 * phase times, the engine's writes between the members and the sweeper's queries.
 */
internal val SAGA_CONNECTION_PATIENCE: Duration = ENGINE_CONFIG.timeoutMs(PetichPhase.EXECUTION).milliseconds

/**
 * The most placement's pass waits for the database in all (B-139), however many of its writes an outage meets.
 *
 * The shopper is waiting for the answer. A member that waits out its phase and the rollback's mark that waits for
 * a database coming back fit inside it — an outage a little longer than the phase is still undone and answered —
 * and a database that stays away is answered `503` after about this long, not after every write's own wait (over
 * 30 s). It is also under [STUCK_AFTER], so a pass that waits is never taken for one a dead process left.
 */
internal val PLACEMENT_PATIENCE: Duration = 20.seconds

/** What placement's saga runs under, made for each pass: its [PLACEMENT_PATIENCE] counts from here. */
internal fun placementPatience(): ConnectionPatience =
    ConnectionPatience(SAGA_CONNECTION_PATIENCE, within = PLACEMENT_PATIENCE)

/**
 * What the sweeper runs under, for as long as it runs: each of its asks waits [SAGA_CONNECTION_PATIENCE] — nobody
 * waits for its answer, and a saga it carries on rides out a short outage as placement's does.
 */
internal fun sweeperPatience(): ConnectionPatience = ConnectionPatience(SAGA_CONNECTION_PATIENCE)

/**
 * How long a saga must sit untouched in `PROCESSING` or `COMPENSATING` before the sweeper takes it as
 * abandoned by a process that died, and carries it on.
 *
 * **A formula, not a taste** (petich: there is no lease, so nothing tells a dead process from a slow
 * one): it must exceed the longest a healthy pass can take, `max(phase timeouts ∪ compensation
 * timeouts)`. The engine keeps petich's defaults, whose largest is AUTHORIZATION's 30 s; this is twice
 * that. A lower number runs a payment twice on a slow day; a higher one leaves a dead process's
 * reservations held for longer.
 */
internal val STUCK_AFTER: Duration = 60.seconds

/**
 * The sweeper the application runs: it re-drives a saga whose process died mid-way — the half of
 * petich's sweeper that is off until a [stuckAfter] is chosen — and reports what it did, because a
 * sweeper that fails every pass looks exactly like one with nothing to sweep (shashki B-95).
 */
internal fun orderSweeper(
    storage: SagaStorage,
    engine: PetichEngine,
    clock: PetichClock,
    stuckAfter: Duration = STUCK_AFTER,
): SuspendedPetichSweeper =
    SuspendedPetichSweeper(
        repository = storage.sagas,
        engine = engine,
        clock = clock,
        stuckAfter = stuckAfter,
        onUnknownType = { log.error("no definition for saga type {}: row {} is skipped", it.type, it.id) },
        onRevived = { log.warn("carried on saga {} after the process running it died", it) },
        onWorkerFailure = { stage, cause -> log.warn("saga sweeper failed at {}: {}", stage, cause.message) },
    )

private object CompensationFailures : CompensationFailureHandler {
    override suspend fun handle(
        e: Exception,
        petich: Petich,
        stepKey: String,
    ) {
        log.error("saga {} could not undo {}; the rollback is retried", petich.id, stepKey, e)
    }

    override suspend fun exhausted(
        petich: Petich,
        stepKey: String,
        attempts: Int,
    ): List<OutboxEvent> {
        log.error("saga {} gave up undoing {} after {} attempts: it needs a person", petich.id, stepKey, attempts)
        return emptyList()
    }
}

private object AnnouncementFailures : AnnouncementFailureHandler {
    override suspend fun failed(
        petich: Petich,
        stepKey: String,
        reason: String,
    ): List<OutboxEvent> {
        log.warn("saga {} completed but {} could not be done: {}", petich.id, stepKey, reason)
        return emptyList()
    }
}
