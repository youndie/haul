package io.github.youndie.haul.ops

import io.github.youndie.vojak.ElectionTiming
import java.sql.Connection
import javax.sql.DataSource

/**
 * How this process shares the store with other replicas of the server (B-174).
 */
internal sealed interface Replicas {
    /**
     * The only one: it runs the simulated world, the sweeper and the sweeps itself, from the first moment it serves,
     * and its pages hear the moves it makes. What a test assembles, and what the server was before B-174.
     */
    data object Alone : Replicas

    /**
     * One of any number on one database — `main` always, whatever the chart's count: the moves travel between the
     * replicas through PostgreSQL ([PostgresBus], each opening [listen] for its own listening connection), and the
     * simulated world, the sweeper and the sweeps run on the elected [Leader] alone, over [leases].
     */
    class Shared(
        val name: String,
        val listen: () -> Connection,
        val leases: DataSource,
        val timing: ElectionTiming = ElectionTiming(),
    ) : Replicas

    companion object {
        /** The bus's channel for the orders that moved. */
        const val MOVES = "haul_order_moves"
    }
}
