package io.github.youndie.haul.ops

import io.github.youndie.vojak.Election
import io.github.youndie.vojak.ElectionTiming
import io.github.youndie.vojak.Leadership
import io.github.youndie.vojak.LockName
import io.github.youndie.vojak.Namespace
import io.github.youndie.vojak.Vojak
import io.github.youndie.vojak.jdbc.JdbcLockStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("io.github.youndie.haul.ops.leader")

/**
 * The replica that runs what must run once (B-174): the simulated world — sellers, couriers, returns, Haul Pay's
 * payments (`FulfilmentRunner`) — the saga's sweeper, the sample sale's re-dating and the demo shoppers' sweep. Every
 * replica serves every route and runs its own placements' sagas; only the leader runs these.
 *
 * Elected with vojak's advisory-lock election on the store's own PostgreSQL ([leases]: a small pool of its own, so a
 * held leadership — one connection for as long as it lasts — never takes one from the requests'). **What it
 * promises is at most one leader at any instant, not at most one effect**: a leader whose session ended (killed,
 * cut off) is replaced once the lock is free and the previous leader's `localLease` is over, and work it had sent
 * may still land. Every one of these passes is safe to run again or twice — a move is a conditional update under a
 * row lock, a capture and a refund are keyed, a stuck saga is re-driven under petich's optimistic version (research
 * D4, «Decided in B-174») — so a handover costs at most a repeated look, never a doubled effect.
 *
 * Shown to operators as `vojak <candidate>` in `pg_stat_activity.application_name`; the candidate is the pod's
 * name.
 */
internal class Leader(
    leases: DataSource,
    private val candidate: String,
    private val timing: ElectionTiming = ElectionTiming(),
) {
    private val vojak = Vojak(JdbcLockStore(leases, TABLE_PREFIX), NAMESPACE)

    /**
     * Campaigns in [scope] until it ends, and runs [work] whenever this replica leads: [work] starts its workers in
     * the scope it is handed, and losing the leadership cancels that scope — the workers with it — before the
     * election says anything but [Leadership.Leader] again. One worker that throws does not stop the others.
     */
    suspend fun elect(
        scope: CoroutineScope,
        work: (CoroutineScope) -> Unit,
    ): Election {
        val election =
            vojak.elect(
                NAME,
                candidate,
                scope,
                timing,
                onStoreFailure = { log.warn("the leader election could not reach the database: {}", it.message) },
                onWorkFailure = { log.error("the leader's work failed; stepping down", it) },
            ) { epoch ->
                log.info(
                    "{} leads: the simulated world, the saga's sweeper and the sweeps run here (epoch {})",
                    candidate,
                    epoch,
                )
                supervisorScope {
                    work(this)
                    awaitCancellation()
                }
            }
        // Every change after the first, which is always Follower and says nothing.
        scope.launch {
            election.leadership.drop(1).collect { state ->
                if (state !is Leadership.Leader) log.info("{} no longer leads: {}", candidate, state)
            }
        }
        return election
    }

    companion object {
        /** One election for all of them: they share a fate, and two would let them land on two replicas for nothing. */
        val NAME: LockName = LockName.of("simulated-world")

        val NAMESPACE: Namespace = Namespace.of("haul")

        /** vojak's one table here is `haul_leader_epoch`, made by the first election. */
        const val TABLE_PREFIX = "haul_leader"

        private val NOT_A_CANDIDATE_CHARACTER = Regex("[^A-Za-z0-9._-]")
        private const val CANDIDATE_LENGTH = 63

        /** [name] as vojak takes a candidate — `[A-Za-z0-9._-]{1,63}` — anything else replaced, since a pod's name is not ours. */
        fun candidate(name: String): String =
            name
                .replace(NOT_A_CANDIDATE_CHARACTER, "-")
                .take(CANDIDATE_LENGTH)
                .ifEmpty { "haul" }
    }
}
