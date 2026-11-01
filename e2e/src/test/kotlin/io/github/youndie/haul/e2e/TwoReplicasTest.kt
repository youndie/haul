package io.github.youndie.haul.e2e

import io.github.youndie.haul.e2e.ComposedStack.Replica
import io.github.youndie.haul.e2e.shoppers.Password
import io.github.youndie.haul.e2e.shoppers.Walk
import io.github.youndie.haul.e2e.shoppers.WalkFailed
import io.github.youndie.haul.e2e.shoppers.Walked
import io.github.youndie.haul.ui.LiveOrdersBody
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Two replicas of the server on one database (B-174), as the composed stack runs them behind [RoundRobinFront]:
 * a move made on one reaches a page connected to the other, and a replica killed in the middle of sagas, under the
 * shoppers' traffic, loses no order and doubles none.
 *
 * Each line that starts «B-174» is part of the record: `docs/research/measurements/b-174/` keeps a run's.
 */
class TwoReplicasTest {
    /**
     * «Live orders» listened to on the replica that is not the leader, and an order placed on the leader: the
     * simulated world runs on the leader, and the placement's saga ran there too, so every frame the follower sends
     * after the first is a move the bus brought. Without it the follower's page would say nothing until reloaded.
     */
    @Test
    fun `a move made on one replica reaches a page listening on the other`() {
        val leader = awaitLeader()
        val follower = ComposedStack.replicas.single { it != leader }
        Storefront(follower.origin).listen(LIVE).use { stream ->
            stream.await(LiveOrdersBody.serializer(), 10.seconds) { true }
            val walked = walk(person("Lee Leader"), leader.origin).take()
            val order = walked.order.substringAfterLast('/')
            val heard =
                stream.await(
                    LiveOrdersBody.serializer(),
                    30.seconds,
                ) { body -> body.orders.any { it.number.endsWith(order) } }
            println(
                "B-174 bus: ${leader.name} placed $order, ${follower.name}'s page drew it: «${heard.orders.first().status}»",
            )
        }
    }

    /**
     * The leader killed — SIGKILL, as a dead node — while sagas are in the middle of their steps on both replicas:
     * PostgreSQL's lock on the delivery windows is held by the test, so every placement waits inside `reserve-slot`,
     * its stock already reserved, until the leader is dead. Then:
     *
     * - the placements the dead replica was running are cut; each walk sends its placement again under the same key,
     *   through the front, to the survivor, which carries the saga on from where it stopped — one order, not two;
     * - one placement nobody sends again (a walk of its own, straight to the leader, that does not resend) is carried
     *   on by the survivor's sweeper, once the survivor leads and the saga has sat untouched for its minute;
     * - the survivor takes the leadership, the simulated world goes on there, and every walk is delivered and
     *   refunded.
     *
     * Afterwards, in the database: no saga unfinished or failed, no order still placing, each person exactly one
     * order, each order one authorisation, its captures its total, its reservation its lines, and the stock what the
     * walks left it — the one order nobody returned holding its units, every other unit back.
     */
    @Test
    fun `a replica killed in the middle of sagas loses no order and doubles none`() {
        val leader = awaitLeader()
        val survivor = ComposedStack.replicas.single { it != leader }
        val stockBefore = ComposedStack.stock()
        val people = List(WALKS) { person("Kim Killed $it") }
        val alone = person("Ada Alone")
        val outcomes = mutableMapOf<String, Result<Walked>>()
        val threads = mutableListOf<Thread>()
        val clock = TimeSource.Monotonic.markNow()

        fun at(): String = clock.elapsedNow().toString(DurationUnit.SECONDS, 2)

        try {
            ComposedStack.connection().use { lock ->
                lock.autoCommit = false
                lock.createStatement().use { it.execute("LOCK TABLE delivery_slots IN EXCLUSIVE MODE") }
                println("B-174 kill: ${at()} the delivery windows locked; the leader is ${leader.name}")

                // The placement nobody sends again: alone first, so the first session waiting on the leader is its.
                threads += walkInThread(alone, leader.origin, outcomes, resend = false)
                awaitWaiting(leader, atLeast = 1, within = 20.seconds)
                val firstWaiting = TimeSource.Monotonic.markNow()
                println("B-174 kill: ${at()} the lone placement waits inside its saga on ${leader.name}")

                people.forEach { threads += walkInThread(it, ComposedStack.origin, outcomes, resend = true) }
                // Until the front has a placement of theirs on the leader waiting too — well inside the saga member's
                // ten seconds, which the lone placement's clock started on.
                while (firstWaiting.elapsedNow() < 6.seconds &&
                    (ComposedStack.front.inFlight(leader.origin) == 0 || waiting(leader) < 2)
                ) {
                    Thread.sleep(50)
                }
                val waitingOnLeader = waiting(leader)
                val waitingOnSurvivor = waiting(survivor)
                val unfinished = sagas().filterKeys { it in UNFINISHED }.values.sum()
                println(
                    "B-174 kill: ${at()} killing ${leader.name}: $waitingOnLeader of its sessions and " +
                        "$waitingOnSurvivor of ${survivor.name}'s wait inside a saga; $unfinished sagas unfinished; " +
                        "the front has " +
                        "${ComposedStack.front.inFlight(leader.origin)} placements on ${leader.name}",
                )
                assertTrue(waitingOnLeader >= 1, "no saga was in the middle of its steps on ${leader.name}")
                leader.kill()
                ComposedStack.front.down(leader.origin)
                val killed = TimeSource.Monotonic.markNow()
                Thread.sleep(300)
                lock.rollback()
                println("B-174 kill: ${at()} ${leader.name} killed, the delivery windows unlocked")

                val took = awaitLeaderIs(survivor, within = 1.minutes)
                println(
                    "B-174 kill: ${at()} ${survivor.name} leads, ${killed.elapsedNow().toString(
                        DurationUnit.SECONDS,
                        2,
                    )} after the kill",
                )
                assertTrue(took, "${survivor.name} did not take the leadership")
            }

            threads.forEach { it.join(4.minutes.inWholeMilliseconds) }
            val cut = ComposedStack.front.cut.toList()
            println("B-174 kill: ${at()} every walk ended; the front cut ${cut.size}: $cut")
            outcomes.toSortedMap().forEach { (who, outcome) ->
                println(
                    "B-174 kill: $who " +
                        outcome.fold({
                            "placed ${it.order.substringAfterLast('/')}, refunded after ${it.refundedAfter}"
                        }, { "stopped: ${it.message}" }),
                )
            }

            // The walks through the front: every one delivered and refunded, but for one a cut it could not send again.
            val failed = people.filter { outcomes[it.login]?.isFailure != false }
            val unsendable = cut.count { !it.resendable && it.upstream == leader.origin }
            assertTrue(
                failed.size <= unsendable,
                "walks stopped: ${failed.map { outcomes[it.login] }}\n${ComposedStack.serverLog()}",
            )
            // The lone walk stopped at its placement, cut and not sent again.
            val lone = assertNotNull(outcomes[alone.login]).exceptionOrNull()
            assertTrue(lone is WalkFailed && "place the order" in lone.step, "the lone walk: $lone")

            val loneOrder = awaitOrder(alone.id, within = 3.minutes)
            println(
                "B-174 kill: ${at()} the lone order $loneOrder is ${status(
                    loneOrder,
                )}, carried on by ${survivor.name}'s sweeper",
            )
            assertEquals("placed", status(loneOrder))

            awaitSagasFinished(within = 1.minutes)
            val statuses = sagas()
            println("B-174 kill: ${at()} the sagas: $statuses")
            assertEquals(emptySet(), statuses.keys intersect (UNFINISHED + BROKEN), "sagas left unfinished or broken")
            assertEquals(0, count("SELECT count(*) FROM orders WHERE status = 'placing'"), "an order still placing")

            val everybody = people.map { it.id } + alone.id
            val orders = ordersOf(everybody)
            assertEquals(
                everybody.associateWith { 1 },
                everybody.associateWith { id ->
                    orders.count { it.second == id }
                },
                "orders per person",
            )
            for ((order, _) in orders) {
                assertEquals(
                    1,
                    count("SELECT count(*) FROM payment_authorisations WHERE order_id = ?", order),
                    "$order authorised",
                )
                val total = count("SELECT total_cents FROM orders WHERE id = ?", order)
                val captured =
                    count("SELECT coalesce(sum(amount_cents), 0) FROM payment_captures WHERE order_id = ?", order)
                if (order != loneOrder) assertEquals(total, captured, "$order captured")
                assertTrue(captured <= total, "$order captured $captured of $total")
                assertEquals(
                    count("SELECT sum(quantity) FROM order_lines WHERE order_id = ?", order),
                    count("SELECT coalesce(sum(quantity), 0) FROM stock_reservations WHERE holder = ?", order),
                    "$order's reservation",
                )
            }
            val kept = ComposedStack.linesOf(loneOrder)
            val changed =
                ComposedStack.stock().filter { (sku, units) -> units != stockBefore[sku] }.mapValues { (sku, units) ->
                    units -
                        stockBefore.getValue(sku)
                }
            assertEquals(kept.mapValues { -it.value }, changed, "the stock after the walks")
        } finally {
            threads.forEach { it.join(10.seconds.inWholeMilliseconds) }
            if (!leader.container.isRunning) {
                leader.replace()
                ComposedStack.front.up(leader.origin)
                println("B-174 kill: ${at()} ${leader.name} replaced, ready")
            }
        }
    }

    private data class Person(
        val id: String,
        val login: String,
    )

    private fun person(name: String): Person {
        val id = "replica-${UUID.randomUUID()}"
        return Person(id, ComposedStack.shildikAdmin.person(id, name, PASSWORD))
    }

    private fun walk(
        person: Person,
        origin: String,
        resend: Boolean = true,
    ): Walk =
        Walk(
            number = 1,
            origin = origin,
            signIn = SignIn(ComposedStack.REDIRECT),
            login = person.login,
            password = Password(PASSWORD),
            cart = ReentrantLock(),
            poll = 500.milliseconds,
            wait = 3.minutes,
            resend = resend,
        )

    private fun walkInThread(
        person: Person,
        origin: String,
        outcomes: MutableMap<String, Result<Walked>>,
        resend: Boolean,
    ): Thread =
        thread(name = "walk ${person.login}") {
            val outcome = runCatching { walk(person, origin, resend).take() }
            synchronized(outcomes) { outcomes[person.login] = outcome }
        }

    private fun awaitLeader(): Replica {
        val since = TimeSource.Monotonic.markNow()
        while (since.elapsedNow() < 1.minutes) {
            ComposedStack.leader()?.let { return it }
            Thread.sleep(200)
        }
        error("no replica leads\n${ComposedStack.serverLog()}")
    }

    private fun awaitLeaderIs(
        replica: Replica,
        within: Duration,
    ): Boolean {
        val since = TimeSource.Monotonic.markNow()
        while (since.elapsedNow() < within) {
            if (ComposedStack.leader() == replica) return true
            Thread.sleep(100)
        }
        return false
    }

    /** [replica]'s sessions waiting on a lock now: its sagas held at the delivery windows. */
    private fun waiting(replica: Replica): Int =
        count(
            "SELECT count(*) FROM pg_stat_activity WHERE application_name = ? AND wait_event_type = 'Lock'",
            "haul ${replica.name}",
        )

    private fun awaitWaiting(
        replica: Replica,
        atLeast: Int,
        within: Duration,
    ) {
        val since = TimeSource.Monotonic.markNow()
        while (waiting(replica) < atLeast) {
            check(since.elapsedNow() < within) {
                "no placement of ${replica.name} reached the delivery windows\n${ComposedStack.serverLog()}"
            }
            Thread.sleep(50)
        }
    }

    private fun sagas(): Map<String, Int> =
        ComposedStack
            .rows("SELECT status, count(*) FROM petiches GROUP BY status") {
                it.getString(1) to
                    it.getInt(2)
            }.toMap()

    private fun awaitSagasFinished(within: Duration) {
        val since = TimeSource.Monotonic.markNow()
        while (sagas().keys.any { it in UNFINISHED } && since.elapsedNow() < within) Thread.sleep(500)
    }

    private fun awaitOrder(
        customerId: String,
        within: Duration,
    ): String {
        val since = TimeSource.Monotonic.markNow()
        while (since.elapsedNow() < within) {
            ordersOf(listOf(customerId))
                .singleOrNull()
                ?.first
                ?.takeIf { status(it) != "placing" }
                ?.let { return it }
            Thread.sleep(1_000)
        }
        error(
            "$customerId's order was not carried on within $within: ${ordersOf(
                listOf(customerId),
            )}\n${ComposedStack.serverLog()}",
        )
    }

    private fun status(order: String): String = ComposedStack.orderStatus(order).first

    private fun ordersOf(customers: List<String>): List<Pair<String, String>> =
        ComposedStack.rows("SELECT id, customer_id FROM orders WHERE customer_id = ANY (?)", customers.toTypedArray()) {
            it.getString(1) to it.getString(2)
        }

    private fun count(
        sql: String,
        vararg parameters: String,
    ): Int = ComposedStack.rows(sql, *parameters) { it.getInt(1) }.single()

    private companion object {
        const val PASSWORD = "replicas-horse-battery-staple"
        const val LIVE = "live:orders"

        /** The walks through the front while the leader dies: enough that the front gives the leader some. */
        const val WALKS = 8

        val UNFINISHED = setOf("PROCESSING", "COMPENSATING", "DRAFT")
        val BROKEN = setOf("FAILED", "COMPENSATION_FAILED")
    }
}
